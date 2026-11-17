# FamLog 앱 아이콘 (B · 기록하는 집)

색: 배경 #FFF1EB (brand/subtle), 마크 #C94A26 (brand/primary). 마크는 적응형 아이콘 안전 영역(66dp) 안에 맞춤(80%).

| 경로 | 용도 |
| --- | --- |
| play-store/famlog-play-store-512.png | Google Play 스토어 아이콘 (512×512, 32bit PNG, 모서리 마스크는 Play가 적용) |
| android/mipmap-*/ic_launcher_foreground.png, ic_launcher_background.png | 적응형 아이콘 레이어 (108dp: 108~432px) |
| android/mipmap-*/ic_launcher.png, ic_launcher_round.png | 일반 런처 아이콘 (48dp: 48~192px) |
| preview/*.png | 원형·둥근 사각 1024 미리보기 (문서·발표용) |
| svg/ | 원본 벡터 (foreground, background, 합성본) |

minSdk 26 이상이라 실제 앱은 적응형 아이콘(mipmap-anydpi-v26)을 쓴다. 전경은 PNG보다 벡터(XML) 레이어를 권장.
