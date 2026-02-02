# sfapi
Android application that uses Scalefusion API.

## Configuration

Set your Scalefusion API token in a `.env` file at the project root (not committed):

```
SCALEFUSION_API_TOKEN=your_token_here
```

The build also accepts `SCALEFUSION_API_TOKEN` from the environment or `local.properties` as fallbacks.

On first launch the app reads the device Android ID, calls `https://api.scalefusion.com/api/v3/devices.json`, and stores the returned Scalefusion device ID for future runs.
