set -e
cd "$(dirname "$0")"
command -v uv >/dev/null || curl -LsSf https://astral.sh/uv/install.sh | sh
export PATH="$HOME/.local/bin:$PATH"
uv venv --python 3.12 .venv
uv pip install --python .venv "tensorflow==2.18.*" pillow pyarrow numpy
.venv/bin/python -c "import tensorflow as tf; print('TF', tf.__version__)"
