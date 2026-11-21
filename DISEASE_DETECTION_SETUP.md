# Disease object detection integration

The scan order is tomato leaf validation, one disease model, then OpenCV affected-area assessment for the selected disease. Early Blight and Spotted Wilt are ordinary classes of the single disease model; there is no separate specialist model or `/refine-disease` request.

## Online model (after training)

The mobile app uses the same deployed website API selected by `ApiConfig.VALIDATION_SERVER_URL` (currently `https://tomatoinsight.onrender.com/`). Set these private environment variables there:

```text
ULTRALYTICS_LEAF_VALIDATION_URL=<leaf validator deployment URL>
ULTRALYTICS_VALIDATION_API_KEY=<leaf validator API key>
ULTRALYTICS_DISEASE_DETECTION_URL=<disease detector deployment URL>
ULTRALYTICS_DISEASE_DETECTION_API_KEY=<disease detector API key>
```

The website server adds `/predict` to the URL if needed. Keep API keys on the server, outside the Android app and repository. The mobile app applies the same 50% leaf gate and 20% disease detection gate as the website. Detection confidence is not affected-area percentage.

Mobile validates the leaf and detects disease on the phone in both modes, using the full photo. When connected, mobile requests the website's leaf isolation mask from `/classify` and sends the mask, original image and on-device diagnosis to `/assess-severity`. If there is no mask, the severity service segments the original image itself. Offline scans defer severity assessment until a connection is available.

## Offline model

`app/src/main/assets/models/diseaseclass03.tflite` is the new Ultralytics object detector. Its embedded metadata lists input float32 `[1,3,640,640]`, output float32 `[1,8,8400]`, and classes in this order: `early_blight`, `tlcv`, `spotted_wilt`, `healthy`. The Android decoder takes the strongest valid box for each class and reports detections at 20% confidence or higher, matching the website. It keeps the existing leaf check before disease detection and waits for the online OpenCV route to assess affected area. The model's box confidence is never used as affected-area percentage.
