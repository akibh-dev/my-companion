# Offline map for saved places

The place editor shows a map from a **PMTiles** file on the phone, so no map tiles come from the
internet (decision D4, plan §10). The internet is used once, on your computer, to make the file.

## 1. Make the file (on the computer)

Easiest: cut Bangladesh out of a Protomaps daily build with the `pmtiles` tool
(<https://github.com/protomaps/go-pmtiles/releases>):

```bash
# Pick a recent build date from https://maps.protomaps.com/builds/
pmtiles extract https://build.protomaps.com/20261020.pmtiles map.pmtiles \
    --bbox=88.0,20.6,92.7,26.7 --maxzoom=15
```

`--maxzoom=15` is street level; drop to 14 for a smaller file. Building from the Geofabrik
Bangladesh extract with Planetiler also works (OpenMapTiles schema); the app's style draws both.

## 2. Copy it to the phone

```bash
adb push map.pmtiles /sdcard/Android/data/com.akib.mycompanion/files/maps/map.pmtiles
```

Open **Home → Context rules → Saved places → Add place**: the map appears. Tap to drop the pin, set
the radius, save. The map shows "© OpenStreetMap contributors" as the ODbL licence requires.

## 3. Updating

Run step 1 again with a newer build date and push the new file over the old one (plan task M4).

## Notes
- No labels are drawn (they'd need font files on the phone); roads, water, parks and buildings are.
- Without the file, places still work: use **Use my current location** or paste coordinates.
- Spike S10 records the file size and how smoothly it draws on the CMF Phone 1.
