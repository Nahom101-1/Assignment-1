# Docker image archive

The Docker image archive is split into two parts to stay below GitHub's per-file upload limit.

Reassemble it in the `dist/` directory with:

```bash
cat ass1-solution-image.tar.gz.part-* > ass1-solution-image.tar.gz
```

Then load it into Docker with:

```bash
docker load -i ass1-solution-image.tar.gz
```

