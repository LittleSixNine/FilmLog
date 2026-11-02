"""Reject UI icon codes absent from the original packaged Phosphor cmap."""
from pathlib import Path
import re,struct
root=Path(__file__).resolve().parent.parent
codes={int(x,16) for x in re.findall(r'0x(e[0-9a-fA-F]{3})(?![0-9a-fA-F])','\n'.join((root/'src/cn/sixnine/filmlog'/name).read_text() for name in ('MainActivity.java','MainNavigation.java','CatalogActivity.java','MeterActivity.java','RollWizard.java','StockLibraryActivity.java','ExposurePresets.java','PresetStore.java','ExposureTimerActivity.java','ScanViewerActivity.java')))}
for name in ("Phosphor.ttf","Phosphor-Fill.ttf","Phosphor-Bold.ttf"):
 b=(root/'assets/fonts'/name).read_bytes()
 u=lambda offset:struct.unpack_from('>H',b,offset)[0]
 d=lambda offset:struct.unpack_from('>I',b,offset)[0]
 cmap=next(d(12+i*16+8) for i in range(u(4)) if b[12+i*16:16+i*16]==b'cmap')
 off=next(cmap+d(cmap+8+i*8) for i in range(u(cmap+2)) if u(cmap+d(cmap+8+i*8))==4)
 n=u(off+6)//2;ends=off+14;starts=ends+2*n+2;deltas=starts+2*n;ranges=deltas+2*n
 for code in codes:
  glyph=0
  for j in range(n):
   if u(starts+j*2)<=code<=u(ends+j*2):
    span=u(ranges+j*2)
    glyph=(code+u(deltas+j*2))&65535 if not span else u(ranges+j*2+span+2*(code-u(starts+j*2)))
    if span and glyph:glyph=(glyph+u(deltas+j*2))&65535
    break
  assert glyph, f'Original Phosphor font has no icon U+{code:04X}'
print(f"PASS: {len(codes)} UI icon codes present in all three original fonts")
