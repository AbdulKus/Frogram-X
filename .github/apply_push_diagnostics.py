from pathlib import Path

def read(path):
    return Path(path).read_text()

def write(path, text):
    Path(path).write_text(text)

def require_once(text, marker, label):
    count = text.count(marker)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")

def insert_after(path, marker, addition):
    text = read(path)
    require_once(text, marker, path + " marker")
    write(path, text.replace(marker, marker + addition, 1))

def insert_before(path, marker, addition):
    text = read(path)
    require_once(text, marker, path + " marker")
    write(path, text.replace(marker, addition + marker, 1))

def section(text, start_marker, end_marker, label):
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"{label}: start marker not found")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"{label}: end marker not found")
    return start, end, text[start:end]

def replace_section(path, start_marker, end_marker, transform):
    text = read(path)
    start, end, block = section(text, start_marker, end_marker, path)
    new_block = transform(block)
    if new_block == block:
        raise SystemExit(f"{path}: section transform made no changes")
    write(path, text[:start] + new_block + text[end:])

manager = "app/src/main/java/org/thunderdog/challegram/telegram/TdlibManager.java"
insert_after(
    manager,
    "      this.tokenFullError = fullError;\n",
    '      tgx.bridge.PushDiagnostics.record("token_state", "state=" + newState + (!StringUtils.isEmpty(error) ? ", error=" + error : ""));\n'
)
insert_after(
    manager,
    "      this.token = token;\n",
    '      tgx.bridge.PushDiagnostics.record("token_changed", "type=" + (token != null ? token.getClass().getSimpleName() : "null"));\n'
)

force_method = """\n  public synchronized void forceReregisterDeviceToken (@Nullable RunnableBool after) {
    tgx.bridge.PushDiagnostics.record("manual_reregister", "resetting local device registration");
    for (TdlibAccount account : this) {
      final @Tdlib.Mode int mode = account.tdlibInstanceMode();
      if (mode == Tdlib.Mode.NORMAL || mode == Tdlib.Mode.DEBUG) {
        setDeviceRegistered(account.id, false);
      }
    }
    this.token = null;
    this.tokenState = TokenState.NONE;
    this.tokenError = null;
    this.tokenFullError = null;
    checkDeviceToken(3, success -> {
      tgx.bridge.PushDiagnostics.record("manual_reregister_result", success ? "success" : "failed");
      if (after != null) {
        after.runWithBool(success);
      }
    });
  }
"""
insert_after(
    manager,
    "  public void checkDeviceToken () {\n    checkDeviceToken(null);\n  }\n",
    force_method
)

settings_manager = "app/src/main/java/org/thunderdog/challegram/telegram/TdlibSettingsManager.java"
def patch_set_registered(block):
    marker = "    pmc.apply();\n"
    require_once(block, marker, "setRegisteredDevice pmc.apply")
    return block.replace(
        marker,
        marker + '    tgx.bridge.PushDiagnostics.record("tdlib_device_registered", "accountId=" + accountId + ", userKnown=" + (userId != 0));\n',
        1
    )
replace_section(
    settings_manager,
    "  public static void setRegisteredDevice (",
    "  public static boolean checkRegisteredDeviceToken",
    patch_set_registered
)

def patch_unregister(block):
    marker = "      .apply();\n"
    require_once(block, marker, "unregisterDevice apply")
    return block.replace(
        marker,
        marker + '    tgx.bridge.PushDiagnostics.record("tdlib_device_unregistered", "accountId=" + accountId);\n',
        1
    )
replace_section(
    settings_manager,
    "  public static void unregisterDevice (int accountId) {",
    "  private long nextLocalChatId ()",
    patch_unregister
)

ids = "app/src/main/res/values/ids.xml"
insert_after(
    ids,
    '  <item type="id" name="btn_secret_appFingerprint" />\n',
    '  <item type="id" name="btn_secret_pushRegistration" />\n'
    '  <item type="id" name="btn_secret_pushDiagnostics" />\n'
    '  <item type="id" name="btn_secret_pushRetry" />\n'
    '  <item type="id" name="btn_secret_pushCopy" />\n'
    '  <item type="id" name="btn_secret_pushClear" />\n'
)

controller = "app/src/main/java/org/thunderdog/challegram/ui/SettingsBugController.java"

helpers = r"""  private String getPushRegistrationSummary () {
    if (tdlib == null) {
      return "Unavailable";
    }
    int total = 0;
    int registered = 0;
    for (TdlibAccount account : tdlib.context()) {
      int mode = account.tdlibInstanceMode();
      if (mode == Tdlib.Mode.NORMAL || mode == Tdlib.Mode.DEBUG) {
        total++;
        if (account.isDeviceRegistered()) {
          registered++;
        }
      }
    }
    return total == 0 ? "No authorized accounts" : registered + "/" + total + " registered";
  }

  private String getPushTokenStateSummary () {
    if (tdlib == null) {
      return "Unavailable";
    }
    switch (tdlib.context().getTokenState()) {
      case TdlibManager.TokenState.ERROR:
        return "ERROR" + (!StringUtils.isEmpty(tdlib.context().getTokenError()) ? ": " + tdlib.context().getTokenError() : "");
      case TdlibManager.TokenState.INITIALIZING:
        return "INITIALIZING";
      case TdlibManager.TokenState.OK: {
        TdApi.DeviceToken token = tdlib.context().getToken();
        return "OK (" + (token != null ? token.getClass().getSimpleName() : "null") + ")";
      }
      case TdlibManager.TokenState.NONE:
      default:
        return "NONE";
    }
  }

  private String buildPushDiagnosticsReport () {
    tgx.bridge.PushDiagnostics.initialize(context);
    Settings settings = Settings.instance();
    StringBuilder b = new StringBuilder();
    b.append("Frogram X push diagnostics\n");
    b.append("App: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
    b.append("Package: ").append(context.getPackageName()).append('\n');
    b.append("Token state: ").append(getPushTokenStateSummary()).append('\n');
    b.append("TDLib registration: ").append(getPushRegistrationSummary()).append('\n');
    b.append("Packages received: ").append(settings.getPushMessageStats()).append('\n');

    long receivedTime = settings.getLastReceivedPushMessageReceivedTime();
    b.append("Last push received: ");
    b.append(receivedTime != 0 ? Lang.getTimestamp(receivedTime, TimeUnit.MILLISECONDS) : "No data");
    b.append('\n');

    long sentTime = settings.getLastReceivedPushMessageSentTime();
    if (receivedTime != 0 && sentTime != 0) {
      b.append("Last push delay: ").append(receivedTime - sentTime).append(" ms\n");
    }
    b.append("Last push TTL: ").append(settings.getLastReceivedPushMessageTtl()).append('\n');
    b.append("Push service: ").append(TdlibNotificationUtils.getDeviceTokenRetriever().name).append('\n');
    b.append("App fingerprint: ").append(U.getApkFingerprint("SHA1")).append("\n\n");
    b.append(tgx.bridge.PushDiagnostics.report());
    return b.toString();
  }

"""
insert_before(
    controller,
    "  @Override\n  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {\n",
    helpers
)
insert_after(
    controller,
    "  protected void onCreateView (Context context, CustomRecyclerView recyclerView) {\n",
    "    if (section == Section.PUSH) {\n"
    "      tgx.bridge.PushDiagnostics.initialize(context);\n"
    "    }\n"
)

valued_marker = (
    "        } else if (itemId == R.id.btn_secret_appFingerprint) {\n"
    '          view.setData(U.getApkFingerprint("SHA1"));\n'
)
insert_after(
    controller,
    valued_marker,
    "        } else if (itemId == R.id.btn_secret_pushRegistration) {\n"
    "          view.setData(getPushRegistrationSummary());\n"
    "        } else if (itemId == R.id.btn_secret_pushDiagnostics) {\n"
    "          tgx.bridge.PushDiagnostics.initialize(context);\n"
    "          view.setData(tgx.bridge.PushDiagnostics.summary());\n"
)

def patch_push_section(block):
    token_row = '        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_pushToken, 0, "Token", false));\n'
    require_once(block, token_row, "push token row")
    block = block.replace(
        token_row,
        token_row +
        "        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));\n" +
        '        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_pushRegistration, 0, "TDLib registration", false));\n',
        1
    )

    fingerprint_row = '        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_appFingerprint, 0, "App fingerprint", false));\n'
    require_once(block, fingerprint_row, "push fingerprint row")
    block = block.replace(
        fingerprint_row,
        fingerprint_row +
        "        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));\n" +
        '        items.add(new ListItem(ListItem.TYPE_VALUED_SETTING_COMPACT, R.id.btn_secret_pushDiagnostics, 0, "Diagnostic history", false));\n',
        1
    )

    tail = "        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));\n        break;\n"
    require_once(block, tail, "push section tail")
    actions = (
        "        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));\n"
        "\n"
        '        items.add(new ListItem(ListItem.TYPE_HEADER, 0, 0, "Diagnostics", false));\n'
        "        items.add(new ListItem(ListItem.TYPE_SHADOW_TOP));\n"
        '        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_pushRetry, 0, "Check & re-register push", false));\n'
        "        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));\n"
        '        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_pushCopy, 0, "Copy full diagnostics", false));\n'
        "        items.add(new ListItem(ListItem.TYPE_SEPARATOR_FULL));\n"
        '        items.add(new ListItem(ListItem.TYPE_SETTING, R.id.btn_secret_pushClear, 0, "Clear diagnostic history", false));\n'
        "        items.add(new ListItem(ListItem.TYPE_SHADOW_BOTTOM));\n"
        '        items.add(new ListItem(ListItem.TYPE_DESCRIPTION, 0, 0, "History is stored locally. Message payloads and full push tokens are not saved.", false));\n'
        "        break;\n"
    )
    return block.replace(tail, actions, 1)

replace_section(
    controller,
    "      case Section.PUSH: {",
    "      case Section.EXPERIMENTS: {",
    patch_push_section
)

def patch_token_listener(block):
    marker = "      adapter.updateValuedSettingById(R.id.btn_secret_pushToken);\n"
    require_once(block, marker, "token listener update")
    return block.replace(
        marker,
        marker +
        "      adapter.updateValuedSettingById(R.id.btn_secret_pushRegistration);\n"
        "      adapter.updateValuedSettingById(R.id.btn_secret_pushDiagnostics);\n",
        1
    )
replace_section(
    controller,
    "  public void onTokenStateChanged (",
    "  @Override\n  public void onNewPushReceived",
    patch_token_listener
)

def patch_push_listener(block):
    marker = "      adapter.updateValuedSettingById(R.id.btn_secret_pushTtl);\n"
    require_once(block, marker, "push listener ttl update")
    return block.replace(
        marker,
        marker +
        "      adapter.updateValuedSettingById(R.id.btn_secret_pushRegistration);\n"
        "      adapter.updateValuedSettingById(R.id.btn_secret_pushDiagnostics);\n",
        1
    )
replace_section(
    controller,
    "  public void onNewPushReceived (",
    "  @Override\n  public void destroy ()",
    patch_push_listener
)

def patch_onclick(block):
    marker = (
        "    } else if (viewId == R.id.btn_secret_pushStats) {\n"
        "      UI.copyText(Settings.instance().getPushMessageStats(), R.string.CopiedText);\n"
    )
    require_once(block, marker, "push stats click")
    actions = (
        marker +
        "    } else if (viewId == R.id.btn_secret_pushDiagnostics || viewId == R.id.btn_secret_pushCopy) {\n"
        "      UI.copyText(buildPushDiagnosticsReport(), R.string.CopiedText);\n"
        "    } else if (viewId == R.id.btn_secret_pushRetry) {\n"
        "      tgx.bridge.PushDiagnostics.initialize(context);\n"
        '      tgx.bridge.PushDiagnostics.record("manual_reregister", "requested from Push Services");\n'
        "      adapter.updateValuedSettingById(R.id.btn_secret_pushDiagnostics);\n"
        "      tdlib.context().forceReregisterDeviceToken(success -> runOnUiThreadOptional(() -> {\n"
        "        if (adapter != null) {\n"
        "          adapter.updateValuedSettingById(R.id.btn_secret_pushToken);\n"
        "          adapter.updateValuedSettingById(R.id.btn_secret_pushRegistration);\n"
        "          adapter.updateValuedSettingById(R.id.btn_secret_pushDiagnostics);\n"
        "        }\n"
        '        UI.showToast(success ? "Push re-registration completed" : "Push re-registration failed. See diagnostics.", Toast.LENGTH_SHORT);\n'
        "      }));\n"
        "    } else if (viewId == R.id.btn_secret_pushClear) {\n"
        "      tgx.bridge.PushDiagnostics.initialize(context);\n"
        "      tgx.bridge.PushDiagnostics.clear();\n"
        "      adapter.updateValuedSettingById(R.id.btn_secret_pushDiagnostics);\n"
        '      UI.showToast("Push diagnostic history cleared", Toast.LENGTH_SHORT);\n'
    )
    return block.replace(marker, actions, 1)

replace_section(
    controller,
    "  @Override\n  public void onClick (View v) {",
    '  @SuppressLint("ResourceType")\n  private void showTdlibVerbositySettings',
    patch_onclick
)
