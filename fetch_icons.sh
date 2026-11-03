#!/bin/bash
cd "$(dirname "$0")"

BRANDS=(
  "apple"
  "samsung"
  "google"
  "sony"
  "nintendo"
  "microsoft"
  "intel"
  "cisco"
  "xiaomi"
  "huawei"
  "dell"
  "hp"
  "lg"
  "asus"
  "amazon"
  "lenovo"
  "tp-link"
  "netgear"
  "ubiquiti"
  "aruba"
  "motorola"
  "nokia"
  "htc"
  "zte"
  "oneplus"
)

for brand in "${BRANDS[@]}"; do
  echo "Fetching $brand..."
  curl -s -L "https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/${brand}.svg" -o "ic_brand_${brand/-/_}.svg"
  if grep -q "404: Not Found" "ic_brand_${brand/-/_}.svg"; then
    echo "Not found: $brand"
    rm "ic_brand_${brand/-/_}.svg"
  else
    npx --yes svg2vd -i "ic_brand_${brand/-/_}.svg" -o "ic_brand_${brand/-/_}.xml"
    rm "ic_brand_${brand/-/_}.svg"
  fi
done
