# third_party

FFmpeg uses submodule path:

`third_party/FFmpeg-n6.1.4`

Initialize with:

```bash
git submodule update --init --recursive
```

Then build Android outputs with:

```bash
bash scripts/build_ffmpeg_android.sh
```
