import org.json.*;
import org.thunderdog.challegram.util.GitHubRelease;

public final class GitHubReleaseTest {
  static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
  static int checks;
  static JSONObject asset(String name, long id) throws Exception {
    return new JSONObject().put("id", id).put("name", name).put("state", "uploaded").put("size", 300)
      .put("digest", "sha256:" + HASH)
      .put("browser_download_url", "https://github.com/AbdulKus/Frogram-X/releases/download/build-83/" + name);
  }
  static JSONObject release() throws Exception {
    return new JSONObject().put("id", 83).put("tag_name", "build-83").put("name", "Frogram X · 83")
      .put("body", "## Changes\n- Fixes").put("draft", false).put("prerelease", false)
      .put("assets", new JSONArray().put(asset("app.apk", 1)).put(asset("update.json", 2)));
  }
  static JSONObject manifest() throws Exception {
    return new JSONObject().put("schema", 1).put("package_name", "org.frogram.messenger")
      .put("artifacts", new JSONArray().put(new JSONObject().put("name", "app.apk").put("version_code", 1879302)
        .put("version_name", "0.28.11.1879-arm64-v8a").put("min_sdk", 24)
        .put("abis", new JSONArray().put("arm64-v8a")).put("sha256", HASH).put("size", 300)));
  }
  static GitHubRelease select(JSONObject r, JSONObject m, long current, int sdk, String... abis) throws Exception {
    return GitHubRelease.select(r, m, "org.frogram.messenger", current, sdk, abis);
  }
  static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("Check " + checks); }
  interface Action { void run() throws Exception; }
  static void rejects(Action action) throws Exception {
    checks++;
    try { action.run(); } catch (java.io.IOException | JSONException expected) { return; }
    throw new AssertionError("Expected rejection: " + checks);
  }
  public static void main(String[] args) throws Exception {
    GitHubRelease r = select(release(), manifest(), 1796302, 36, "arm64-v8a");
    check(r != null && r.versionCode == 1879302 && r.notes.contains("Fixes"));
    check(GitHubRelease.manifestUrl(release()).endsWith("/update.json"));
    check(select(release(), manifest(), 1879302, 36, "arm64-v8a") == null);
    check(select(release(), manifest(), 1999302, 36, "arm64-v8a") == null);
    check(select(release(), manifest(), 1796302, 23, "arm64-v8a") == null);
    check(select(release(), manifest(), 1796302, 36, "x86_64") == null);
    check(select(release().put("draft", true), manifest(), 1, 36, "arm64-v8a") == null);
    check(select(release().put("prerelease", true), manifest(), 1, 36, "arm64-v8a") == null);
    rejects(() -> select(release(), manifest().put("package_name", "other.app"), 1, 36, "arm64-v8a"));
    rejects(() -> select(release(), manifest().put("schema", 2), 1, 36, "arm64-v8a"));
    JSONObject m = manifest(); m.getJSONArray("artifacts").getJSONObject(0).put("sha256", "bad");
    rejects(() -> select(release(), m, 1, 36, "arm64-v8a"));
    JSONObject wrongSize = release(); wrongSize.getJSONArray("assets").getJSONObject(0).put("size", 99);
    rejects(() -> select(wrongSize, manifest(), 1, 36, "arm64-v8a"));
    JSONObject wrongHost = release(); wrongHost.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://github.com.evil.test/asset.apk");
    rejects(() -> select(wrongHost, manifest(), 1, 36, "arm64-v8a"));
    JSONObject wrongRepo = release(); wrongRepo.getJSONArray("assets").getJSONObject(0).put("browser_download_url", "https://github.com/Other/App/releases/download/build-83/app.apk");
    rejects(() -> select(wrongRepo, manifest(), 1, 36, "arm64-v8a"));
    JSONObject wrongHash = release(); wrongHash.getJSONArray("assets").getJSONObject(0).put("digest", "sha256:bad");
    rejects(() -> select(wrongHash, manifest(), 1, 36, "arm64-v8a"));
    JSONObject incomplete = release(); incomplete.getJSONArray("assets").getJSONObject(0).put("state", "new");
    rejects(() -> select(incomplete, manifest(), 1, 36, "arm64-v8a"));
    JSONObject missing = release().put("assets", new JSONArray());
    rejects(() -> select(missing, manifest(), 1, 36, "arm64-v8a"));
    check(GitHubRelease.manifestUrl(missing) == null);
    JSONObject duplicate = release(); duplicate.getJSONArray("assets").put(asset("app.apk", 3));
    rejects(() -> select(duplicate, manifest(), 1, 36, "arm64-v8a"));
    System.out.println("Passed " + checks + " release selection and validation checks");
  }
}
