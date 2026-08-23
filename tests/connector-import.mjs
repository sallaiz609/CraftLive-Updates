import { TikTokLiveConnection, WebcastEvent } from "tiktok-live-connector";

if (typeof TikTokLiveConnection !== "function" || !WebcastEvent?.GIFT) {
  throw new Error("A TikTok LIVE connector várt exportjai hiányoznak.");
}

console.log("TikTok connector import OK");
