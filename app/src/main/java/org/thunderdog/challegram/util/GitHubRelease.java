package org.thunderdog.challegram.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;

/** Validates the public release and its APK manifest before anything is downloaded. */
public final class GitHubRelease {
  public static final String REPOSITORY = "AbdulKus/Frogram-X";
  public static final String API_URL = "https://api.github.com/repos/" + REPOSITORY + "/releases/latest";
  public static final String RELEASES_URL = "https://github.com/" + REPOSITORY + "/releases";

  public final long releaseId, assetId, size, versionCode;
  public final String title, notes, versionName, downloadUrl, sha256;

  private GitHubRelease (JSONObject release, JSONObject artifact, JSONObject asset) throws JSONException {
    releaseId = release.getLong("id");
    assetId = asset.getLong("id");
    size = artifact.getLong("size");
    versionCode = artifact.getLong("version_code");
    versionName = artifact.getString("version_name");
    title = release.optString("name", release.getString("tag_name"));
    notes = release.optString("body", "");
    downloadUrl = asset.getString("browser_download_url");
    sha256 = artifact.getString("sha256");
  }

  public static boolean isPublished (JSONObject release) {
    return !release.optBoolean("draft", true) && !release.optBoolean("prerelease", true);
  }

  public static String manifestUrl (JSONObject release) throws JSONException, IOException {
    JSONObject asset = findAsset(release, "update.json");
    if (asset == null) return null; // Older releases predate in-app updates.
    return validatedAssetUrl(release, asset);
  }

  public static GitHubRelease select (JSONObject release, JSONObject manifest, String packageName,
                                      long installedVersion, int sdk, String[] supportedAbis) throws JSONException, IOException {
    if (!isPublished(release)) return null;
    if (manifest.getInt("schema") != 1 || !packageName.equals(manifest.getString("package_name"))) {
      throw new IOException("Unsupported update manifest");
    }
    GitHubRelease best = null;
    int bestRank = Integer.MAX_VALUE;
    JSONArray artifacts = manifest.getJSONArray("artifacts");
    for (int i = 0; i < artifacts.length(); i++) {
      JSONObject artifact = artifacts.getJSONObject(i);
      long version = artifact.getLong("version_code");
      if (version <= installedVersion || artifact.getInt("min_sdk") > sdk) continue;
      JSONArray abis = artifact.getJSONArray("abis");
      int rank = Integer.MAX_VALUE;
      for (int j = 0; j < supportedAbis.length; j++) {
        for (int k = 0; k < abis.length(); k++) {
          if (supportedAbis[j].equals(abis.getString(k))) rank = Math.min(rank, j);
        }
      }
      if (rank == Integer.MAX_VALUE) continue;
      String name = artifact.getString("name");
      if (!name.endsWith(".apk") || name.contains("/") || name.contains("\\")) throw new IOException("Invalid APK name");
      JSONObject asset = findAsset(release, name);
      if (asset == null) throw new IOException("APK asset is missing");
      validatedAssetUrl(release, asset);
      long size = artifact.getLong("size");
      if (size <= 0 || size > 512L * 1024 * 1024 || size != asset.getLong("size") || asset.getLong("id") <= 0) {
        throw new IOException("Invalid APK size or ID");
      }
      String digest = artifact.getString("sha256");
      if (!digest.matches("[0-9a-f]{64}")) throw new IOException("Missing APK checksum");
      String githubDigest = asset.optString("digest", "");
      if (githubDigest.startsWith("sha256:") && !githubDigest.equals("sha256:" + digest)) {
        throw new IOException("Conflicting APK checksums");
      }
      if (best == null || version > best.versionCode || (version == best.versionCode && rank < bestRank)) {
        best = new GitHubRelease(release, artifact, asset);
        bestRank = rank;
      }
    }
    return best;
  }

  private static JSONObject findAsset (JSONObject release, String name) throws JSONException, IOException {
    JSONArray assets = release.getJSONArray("assets");
    JSONObject found = null;
    for (int i = 0; i < assets.length(); i++) {
      JSONObject asset = assets.getJSONObject(i);
      if (name.equals(asset.getString("name")) && "uploaded".equals(asset.optString("state"))) {
        if (found != null) throw new IOException("Duplicate release asset");
        found = asset;
      }
    }
    return found;
  }

  private static String validatedAssetUrl (JSONObject release, JSONObject asset) throws JSONException, IOException {
    String url = asset.getString("browser_download_url");
    try {
      URI uri = new URI(url);
      String path = "/" + REPOSITORY + "/releases/download/" + release.getString("tag_name") + "/" + asset.getString("name");
      if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) || uri.getPort() != -1 ||
          uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || !path.equals(uri.getPath())) {
        throw new IOException("Unexpected release asset URL");
      }
      return url;
    } catch (java.net.URISyntaxException e) {
      throw new IOException("Invalid release asset URL", e);
    }
  }

}
