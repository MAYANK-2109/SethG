"""
Trains the on-device e-waste photo classifier (MobileNetV3-Small, transfer learning)
and exports it as a quantized TFLite model for the Android app.

Output:
  runs/ewaste_classifier.tflite   ~1–2 MB, input float32 [1,224,224,3] RGB 0–255
  runs/labels.txt                 index → class name (ewaste, not_ewaste)

Run:  .venv/bin/python train.py [extra_test_image.jpg ...]
"""
import sys
from pathlib import Path

import numpy as np
import tensorflow as tf

ROOT = Path(__file__).parent
DATA = ROOT / "data" / "prepared"
RUNS = ROOT / "runs"
IMG_SIZE = (224, 224)
BATCH = 32
SEED = 42


def load_datasets():
    common = dict(
        validation_split=0.2, seed=SEED, image_size=IMG_SIZE,
        batch_size=BATCH, label_mode="int",
    )
    train = tf.keras.utils.image_dataset_from_directory(DATA, subset="training", **common)
    val = tf.keras.utils.image_dataset_from_directory(DATA, subset="validation", **common)
    return train, val


def class_weights(train_ds, num_classes):
    counts = np.zeros(num_classes)
    for _, y in train_ds.unbatch():
        counts[int(y)] += 1
    total = counts.sum()
    return {i: float(total / (num_classes * c)) for i, c in enumerate(counts)}, counts


def build_model(num_classes):
    augment = tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(0.1),
        tf.keras.layers.RandomZoom(0.2),
        tf.keras.layers.RandomContrast(0.2),
        tf.keras.layers.RandomBrightness(0.2, value_range=(0, 255)),
    ], name="augment")

    base = tf.keras.applications.MobileNetV3Small(
        input_shape=IMG_SIZE + (3,), include_top=False, weights="imagenet",
        include_preprocessing=True, pooling="avg",   # takes raw 0–255 RGB
    )
    base.trainable = False

    inputs = tf.keras.Input(IMG_SIZE + (3,))
    x = augment(inputs)
    x = base(x, training=False)
    x = tf.keras.layers.Dropout(0.3)(x)
    head = tf.keras.layers.Dense(num_classes, activation="softmax", name="head")
    outputs = head(x)
    return tf.keras.Model(inputs, outputs), base, head


def evaluate(model, val_ds, class_names):
    y_true, y_pred = [], []
    for x, y in val_ds:
        y_true += list(y.numpy())
        y_pred += list(np.argmax(model.predict(x, verbose=0), axis=1))
    cm = tf.math.confusion_matrix(y_true, y_pred, num_classes=len(class_names)).numpy()
    print("\nConfusion matrix (rows = true, cols = predicted):", class_names)
    print(cm)
    for i, name in enumerate(class_names):
        recall = cm[i, i] / max(cm[i].sum(), 1)
        precision = cm[i, i] / max(cm[:, i].sum(), 1)
        print(f"  {name:<11} precision {precision:.3f}  recall {recall:.3f}")
    print(f"  accuracy {np.trace(cm) / cm.sum():.3f}")


def export_tflite(base, head, class_names):
    # Inference graph = trained base + head, without augmentation / dropout
    inputs = tf.keras.Input(IMG_SIZE + (3,))
    inference = tf.keras.Model(inputs, head(base(inputs, training=False)))

    converter = tf.lite.TFLiteConverter.from_keras_model(inference)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]   # dynamic-range int8 weights
    tflite = converter.convert()

    RUNS.mkdir(exist_ok=True)
    (RUNS / "ewaste_classifier.tflite").write_bytes(tflite)
    (RUNS / "labels.txt").write_text("\n".join(class_names) + "\n")
    print(f"\nSaved runs/ewaste_classifier.tflite ({len(tflite) / 1e6:.2f} MB)")
    return tflite


def tflite_predict(tflite, paths, class_names):
    interp = tf.lite.Interpreter(model_content=tflite)
    interp.allocate_tensors()
    inp, out = interp.get_input_details()[0], interp.get_output_details()[0]
    for p in paths:
        img = tf.keras.utils.load_img(p, target_size=IMG_SIZE)
        arr = np.expand_dims(np.asarray(img, dtype=np.float32), 0)
        interp.set_tensor(inp["index"], arr)
        interp.invoke()
        probs = interp.get_tensor(out["index"])[0]
        print(f"  {Path(p).name:<30} " + "  ".join(f"{n}={v:.2f}" for n, v in zip(class_names, probs)))


def main():
    tf.keras.utils.set_random_seed(SEED)
    train_ds, val_ds = load_datasets()
    class_names = train_ds.class_names
    weights, counts = class_weights(train_ds, len(class_names))
    print("classes", class_names, "train counts", counts, "weights", weights)

    train_ds = train_ds.prefetch(tf.data.AUTOTUNE)
    val_ds = val_ds.prefetch(tf.data.AUTOTUNE)
    model, base, head = build_model(len(class_names))

    # Stage 1: train the new head only
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-3), loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    model.fit(train_ds, validation_data=val_ds, epochs=8, class_weight=weights)

    # Stage 2: fine-tune the top of MobileNetV3 (BatchNorm stays frozen)
    base.trainable = True
    for layer in base.layers[:-40]:
        layer.trainable = False
    for layer in base.layers:
        if isinstance(layer, tf.keras.layers.BatchNormalization):
            layer.trainable = False
    model.compile(optimizer=tf.keras.optimizers.Adam(1e-5), loss="sparse_categorical_crossentropy", metrics=["accuracy"])
    model.fit(
        train_ds, validation_data=val_ds, epochs=8, class_weight=weights,
        callbacks=[tf.keras.callbacks.EarlyStopping(patience=3, restore_best_weights=True)],
    )

    evaluate(model, val_ds, class_names)
    tflite = export_tflite(base, head, class_names)
    if len(sys.argv) > 1:
        print("\nTFLite check on extra images:")
        tflite_predict(tflite, sys.argv[1:], class_names)


if __name__ == "__main__":
    main()
