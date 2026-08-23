import { packager } from "@electron/packager";
import { readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const projectRoot = path.resolve(scriptDirectory, "..");
const packageJson = JSON.parse(await readFile(path.join(projectRoot, "package.json"), "utf8"));
const inputHelper = path.join(projectRoot, "native", "bin", "CraftLive.InputHelper.exe");

if (!existsSync(inputHelper)) {
  throw new Error("The native Minecraft input helper is missing. Run npm run build:input-helper on Windows first.");
}

const outputPaths = await packager({
  dir: projectRoot,
  name: "CraftLive",
  platform: "win32",
  arch: "x64",
  out: path.join(projectRoot, "release"),
  overwrite: true,
  asar: true,
  extraResource: [inputHelper],
  prune: true,
  appVersion: packageJson.version,
  download: {
    cacheRoot: process.env.ELECTRON_CACHE || path.join(projectRoot, ".tools", "electron-cache")
  },
  win32metadata: {
    CompanyName: "CraftLive",
    FileDescription: "TikTok LIVE - Minecraft interactions without a mod",
    ProductName: "CraftLive",
    InternalName: "CraftLive"
  },
  ignore: [
    /^\/scripts(?:\/|$)/,
    /^\/tests(?:\/|$)/,
    /^\/KESZ(?:\/|$)/,
    /^\/release(?:\/|$)/,
    /^\/\.tools(?:\/|$)/,
    /^\/\.npm-cache(?:\/|$)/,
    /^\/\.electron-cache(?:\/|$)/,
    /^\/\.gitignore$/,
    /^\/README\.md$/,
    /^\/TELEPITES\.txt$/
  ]
});

if (!outputPaths.length) {
  throw new Error("The Windows application directory was not created.");
}

console.log(`Windows application created: ${outputPaths[0]}`);
