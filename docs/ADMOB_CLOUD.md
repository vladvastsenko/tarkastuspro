# Tarkastus Cloud advertising
Package: fi.vladi.tarkastuscloud.
Original AdMob app and banner IDs are configured in app/build.gradle.kts.
Default builds use Google test IDs; offlinePreview disables banner requests and UMP UI.
Only after confirming AdMob account ownership, package association, published consent message, privacy policy, app readiness and store disclosures, use -PproductionAds=true on a non-preview release build. Do not click live ads during testing.
Existing UMP canRequestAds gate and required privacy options remain in place. No banners in report editors or Custom Report.
Privacy policy source: https://github.com/vladvastsenko/vladi/blob/main/tarkastus-cloud/privacy/index.html
Public URL expected: https://www.vladi.fi/tarkastus-cloud/privacy/
Policy needs updating before release: describe universal inspection reports and user-defined templates rather than dedicated waterproofing reports; disclose custom template storage and image/signature handling. Verify account deletion, Drive behavior and advertising disclosures against final build. Do not claim subscription availability until implemented.
No app-ads.txt was found in website repository tree during review. Verify AdMob's provided publisher record and publish at site's root if required.
AdMob console settings and live site response have not been verified. Firebase unchanged.
