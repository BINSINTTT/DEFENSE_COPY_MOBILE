# Disease object detection integration

The scan order is tomato leaf validation, one disease model, then OpenCV affected-area assessment for the selected disease. Early Blight and Spotted Wilt are ordinary classes of the single disease model; there is no separate specialist model or `/refine-disease` request.

## Online model (after training)

Set these private environment variables on the API deployment used by `ApiConfig.VALIDATION_SERVER_URL` (currently `https://tomatoinsight.onrender.com/`):

```text
ULTRALYTICS_PREDICT_URL=<new disease detection deployment URL>
ULTRALYTICS_API_KEY=<new deployment API key>
DISEASE_CLASSES=Early blight,Spotted wilt,Yellow leaf curl virus,Healthy
DISEASE_MIN_CONFIDENCE=0.25
```

`server/app.py` adds `/predict` to the URL if needed. Keep the API key on the server, outside the Android app and repository. Adjust `DISEASE_CLASSES` to the **exact** exported class names after training; update the Android disease labels and treatment mappings if the new dataset adds or renames classes. The server currently takes the highest confidence box per class to drive the existing four disease bars. A class with no box scores zero. Detection confidence is not affected-area percentage.

The app uses disease predictions returned by `/validate` for online scans and sends the highest disease class to the existing `/assess-severity` OpenCV route on that same host. The OpenCV response supplies affected-area percentage and severity. `server/app.py` contains validation and disease detection, but does not implement `/assess-severity`; keep the live website API with that route as the app host unless OpenCV is also added to a new host. Test the new endpoint response and several representative photos before deployment; deployment JSON shapes can vary.

## Offline model (after TFLite export)

The bundled `app/src/main/assets/models/tomatoinsightdiseaseclassification.tflite` is still the **old four-class classifier** for offline scans. Do not replace that file with the detector export yet. A detector TFLite has different tensor outputs and needs an output decoder, confidence threshold, non-maximum suppression if not embedded in the export, and its exact class-index mapping. Supply the TFLite file plus the export's class list, input/output tensor shapes and data types, and a sample inference response so the offline path can be integrated and checked against the online result.
