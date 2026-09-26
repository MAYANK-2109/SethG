"""Evaluates runs/ewaste_classifier.tflite on the held-out validation split (same seed as train.py)."""
import numpy as np
import tensorflow as tf

import train

_, val = train.load_datasets()
names = val.class_names
interp = tf.lite.Interpreter(model_path=str(train.RUNS / "ewaste_classifier.tflite"))
inp = interp.get_input_details()[0]
interp.resize_tensor_input(inp["index"], [1, 224, 224, 3])
interp.allocate_tensors()
out = interp.get_output_details()[0]

y_true, p_ewaste = [], []
for x, y in val.unbatch():
    interp.set_tensor(inp["index"], x.numpy()[None])
    interp.invoke()
    p_ewaste.append(interp.get_tensor(out["index"])[0][names.index("ewaste")])
    y_true.append(names[int(y)])
y_true, p_ewaste = np.array(y_true), np.array(p_ewaste)

is_e = y_true == "ewaste"
print(f"validation images: {len(y_true)}  (ewaste {is_e.sum()}, not_ewaste {(~is_e).sum()})")
print(f"accuracy @0.5: {np.mean((p_ewaste >= 0.5) == is_e):.3f}")
# App thresholds: accept ≥ 0.65, reject ≤ 0.35, otherwise UNCERTAIN (accepted + flagged)
for label, mask in [("e-waste photos", is_e), ("non-e-waste photos", ~is_e)]:
    p = p_ewaste[mask]
    print(f"  {label:<20} accepted {np.mean(p >= 0.65):6.1%}   uncertain {np.mean((p > 0.35) & (p < 0.65)):6.1%}   rejected {np.mean(p <= 0.35):6.1%}")
