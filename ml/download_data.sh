set -e
cd "$(dirname "$0")/data"
HF=https://huggingface.co/datasets
echo "[1/3] garbage 12-class (250 MB)"
curl -fL --retry 5 -C - -o garbage12.zip "$HF/UdaraChamidu/Garbage-Classification-with-12-classes/resolve/main/garbage_classification.zip"
unzip -q -o garbage12.zip -d garbage12
echo "[2/3] PCB images"
curl -fsL --retry 5 -o pcb.json "https://huggingface.co/api/datasets/keremberke/pcb-defect-segmentation/parquet/full"
python3 - <<'PY'
import json,subprocess
urls=[u for v in json.load(open('pcb.json')).values() for u in v]
for i,u in enumerate(urls): subprocess.run(['curl','-fsL','--retry','5','-o',f'pcb_{i}.parquet',u],check=True)
print('pcb parquet files',len(urls))
PY
echo "[3/3] GIZ scrapyard e-waste (350 photos)"
mkdir -p giz
curl -fsL "https://huggingface.co/api/datasets/GIZ/E-Waste-Database/tree/main" | python3 -c "
import sys,json,random
t=sorted(x['path'] for x in json.load(sys.stdin) if x['path'].lower().endswith('.jpg'))
random.seed(0); random.shuffle(t); print('\n'.join(t[:350]))" > giz_list.txt
n=0; while read f; do n=$((n+1)); [ -s "giz/$f" ] || curl -fsL --retry 5 -o "giz/$f" "$HF/GIZ/E-Waste-Database/resolve/main/$f"; [ $((n%50)) = 0 ] && echo "  giz $n/350"; done < giz_list.txt
echo "DOWNLOADS DONE"; du -sh .
