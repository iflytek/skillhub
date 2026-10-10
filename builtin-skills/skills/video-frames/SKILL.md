---
name: video-frames
description: Extract a single frame from a local video at the first frame, a timestamp, or a zero-based frame index using FFmpeg.
version: 1.0.0
license: MIT
---

# Video Frames (ffmpeg)

Extract a single frame from a video, or create quick thumbnails for inspection.

## Quick start

First frame:

```bash
bash scripts/frame.sh /path/to/video.mp4 --out /tmp/frame.jpg
```

At a timestamp:

```bash
bash scripts/frame.sh /path/to/video.mp4 --time 00:00:10 --out /tmp/frame-10s.jpg
```

At a zero-based frame index:

```bash
bash scripts/frame.sh /path/to/video.mp4 --index 42 --out /tmp/frame-42.png
```

## Notes

- Prefer `--time` for "what is happening around here?".
- Use a `.jpg` for quick share; use `.png` for crisp UI frames.
- `--index` accepts a non-negative integer only.
- The script never overwrites an existing output. Choose a new path or remove the
  old file only after the user explicitly asks to replace it.
