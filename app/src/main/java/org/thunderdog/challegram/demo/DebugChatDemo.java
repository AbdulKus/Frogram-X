package org.thunderdog.challegram.demo;

import androidx.collection.LongSparseArray;

import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.BuildConfig;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.ui.MessagesController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import tgx.td.ChatId;

public class DebugChatDemo {
  public static final long DEMO_CHAT_ID = ChatId.fromSupergroupId(1L);

  public static TdApi.Chat createDemoChat () {
    TdApi.Chat chat = new TdApi.Chat();
    chat.id = DEMO_CHAT_ID;
    chat.title = "Команда Frogram";
    chat.type = new TdApi.ChatTypeSupergroup(1, false);
    chat.notificationSettings = new TdApi.ChatNotificationSettings();
    chat.positions = new TdApi.ChatPosition[0];
    TdApi.ChatPermissions p = new TdApi.ChatPermissions();
    p.canSendBasicMessages = true;
    p.canSendAudios = true;
    p.canSendDocuments = true;
    p.canSendPhotos = true;
    p.canSendVideos = true;
    p.canSendVideoNotes = true;
    p.canSendVoiceNotes = true;
    p.canSendPolls = true;
    p.canSendOtherMessages = true;
    p.canAddLinkPreviews = true;
    p.canChangeInfo = true;
    p.canInviteUsers = true;
    p.canPinMessages = true;
    p.canCreateTopics = true;
    chat.permissions = p;
    return chat;
  }

  public static void openPinnedMessages (ViewController<?> parent) {
    if (!BuildConfig.DEBUG) return;
    Tdlib tdlib = parent.context().currentTdlib();
    if (tdlib.isAuthorized()) return;
    TdApi.Chat chat = createDemoChat();
    MessagesController c = new MessagesController(parent.context(), tdlib);
    MessagesController.Arguments arguments = new MessagesController.Arguments(null, chat, null, null, new TdApi.SearchMessagesFilterPinned());
    arguments.debugDemo = true;
    c.setArguments(arguments);
    parent.navigateTo(c);
  }

  public static void openEventLog (ViewController<?> parent) {
    if (!BuildConfig.DEBUG) return;
    Tdlib tdlib = parent.context().currentTdlib();
    if (tdlib.isAuthorized()) return;
    TdApi.Chat chat = createDemoChat();
    MessagesController c = new MessagesController(parent.context(), tdlib);
    MessagesController.Arguments arguments = new MessagesController.Arguments(MessagesController.PREVIEW_MODE_EVENT_LOG, null, chat);
    arguments.debugDemo = true;
    c.setArguments(arguments);
    parent.navigateTo(c);
  }

  private static TdApi.Usernames createDemoUsernames (String username) {
    return new TdApi.Usernames(new String[]{username}, new String[0], username, new String[0], "");
  }

  public static LongSparseArray<TdApi.User> createDemoParticipants () {
    LongSparseArray<TdApi.User> participants = new LongSparseArray<>();

    TdApi.User u1 = new TdApi.User();
    u1.id = 1L;
    u1.firstName = "Александр";
    u1.lastName = "Волков";
    u1.usernames = createDemoUsernames("alex_volkov");
    u1.status = new TdApi.UserStatusRecently();
    u1.type = new TdApi.UserTypeRegular();
    participants.put(u1.id, u1);

    TdApi.User u2 = new TdApi.User();
    u2.id = 2L;
    u2.firstName = "Екатерина";
    u2.lastName = "Смирнова";
    u2.usernames = createDemoUsernames("kate_smirnova");
    u2.status = new TdApi.UserStatusRecently();
    u2.type = new TdApi.UserTypeRegular();
    participants.put(u2.id, u2);

    TdApi.User u3 = new TdApi.User();
    u3.id = 3L;
    u3.firstName = "Михаил";
    u3.lastName = "Кузнецов";
    u3.usernames = createDemoUsernames("mikhail_k");
    u3.status = new TdApi.UserStatusRecently();
    u3.type = new TdApi.UserTypeRegular();
    participants.put(u3.id, u3);

    TdApi.User u4 = new TdApi.User();
    u4.id = 4L;
    u4.firstName = "Анна";
    u4.lastName = "Морозова";
    u4.usernames = createDemoUsernames("anna_m");
    u4.status = new TdApi.UserStatusRecently();
    u4.type = new TdApi.UserTypeRegular();
    participants.put(u4.id, u4);

    return participants;
  }

  private static TdApi.Message createTextMessage (long id, long userId, int date, String text) {
    TdApi.Message m = new TdApi.Message();
    m.id = id;
    m.chatId = DEMO_CHAT_ID;
    m.senderId = new TdApi.MessageSenderUser(userId);
    m.date = date;
    m.isOutgoing = false;
    m.canBeSaved = true;
    m.content = new TdApi.MessageText(new TdApi.FormattedText(text, new TdApi.TextEntity[0]), null, null);
    return m;
  }

  public static List<TdApi.Message> createDemoPinnedMessages () {
    List<TdApi.Message> list = new ArrayList<>();
    int now = (int) (System.currentTimeMillis() / 1000L);

    list.add(createTextMessage(101L, 1L, now - 86400 * 3,
      "👋 Добро пожаловать в Frogram-X! Здесь собраны правила нашего сообщества, инструкции и полезные ссылки проекта. Пожалуйста, соблюдайте взаимное уважение."));

    list.add(createTextMessage(102L, 2L, now - 86400 * 2,
      "📢 График релизов: новые стабильные версии Frogram-X выходят каждые две недели. О найденных ошибках сообщайте в ветку обсуждений с тегом #bug."));

    list.add(createTextMessage(103L, 3L, now - 86400,
      "🛡 Безопасность: настоятельно рекомендуем настроить двухфакторную аутентификацию (2FA) и облачный пароль в настройках конфиденциальности."));

    list.add(createTextMessage(104L, 1L, now - 3600 * 4,
      "✨ Новый интерфейс: в последнем обновлении добавлен прозрачный стеклянный дизайн чатов с плавающим заголовком и адаптивным размытием фона."));

    list.add(createTextMessage(105L, 4L, now - 1800,
      "🗳 Опрос сообщества: какие новые функции вы хотите увидеть первыми в следующем обновлении? Оставляйте свои предложения в комментариях под этой записью!"));

    for (TdApi.Message message : list) {
      message.isPinned = true;
    }
    Collections.reverse(list);
    return list;
  }

  public static List<TdApi.ChatEvent> createDemoChatEvents () {
    List<TdApi.ChatEvent> list = new ArrayList<>();
    int now = (int) (System.currentTimeMillis() / 1000L);

    // Event 1: Message pinned
    TdApi.ChatEvent e1 = new TdApi.ChatEvent();
    e1.id = 1L;
    e1.date = now - 7200;
    e1.memberId = new TdApi.MessageSenderUser(1L);
    TdApi.ChatEventMessagePinned a1 = new TdApi.ChatEventMessagePinned();
    a1.message = createTextMessage(104L, 1L, now - 7200, "✨ Новый интерфейс: стеклянный дизайн чатов с плавающим заголовком.");
    e1.action = a1;
    list.add(e1);

    // Event 2: Member promoted
    TdApi.ChatEvent e2 = new TdApi.ChatEvent();
    e2.id = 2L;
    e2.date = now - 5400;
    e2.memberId = new TdApi.MessageSenderUser(2L);
    TdApi.ChatEventMemberPromoted a2 = new TdApi.ChatEventMemberPromoted();
    a2.userId = 3L;
    a2.oldStatus = new TdApi.ChatMemberStatusMember();
    TdApi.ChatMemberStatusAdministrator adminStatus = new TdApi.ChatMemberStatusAdministrator();
    adminStatus.canBeEdited = false;
    TdApi.ChatAdministratorRights rights = new TdApi.ChatAdministratorRights();
    rights.canPinMessages = true;
    rights.canDeleteMessages = true;
    rights.canManageVideoChats = true;
    adminStatus.rights = rights;
    a2.newStatus = adminStatus;
    e2.action = a2;
    list.add(e2);

    // Event 3: Slow mode delay changed
    TdApi.ChatEvent e3 = new TdApi.ChatEvent();
    e3.id = 3L;
    e3.date = now - 3600;
    e3.memberId = new TdApi.MessageSenderUser(3L);
    TdApi.ChatEventSlowModeDelayChanged a3 = new TdApi.ChatEventSlowModeDelayChanged();
    a3.oldSlowModeDelay = 0;
    a3.newSlowModeDelay = 10;
    e3.action = a3;
    list.add(e3);

    // Event 4: Title changed
    TdApi.ChatEvent e4 = new TdApi.ChatEvent();
    e4.id = 4L;
    e4.date = now - 2400;
    e4.memberId = new TdApi.MessageSenderUser(2L);
    TdApi.ChatEventTitleChanged a4 = new TdApi.ChatEventTitleChanged();
    a4.oldTitle = "Frogram Chat";
    a4.newTitle = "Команда Frogram";
    e4.action = a4;
    list.add(e4);

    // Event 5: Message edited
    TdApi.ChatEvent e5 = new TdApi.ChatEvent();
    e5.id = 5L;
    e5.date = now - 1200;
    e5.memberId = new TdApi.MessageSenderUser(1L);
    TdApi.ChatEventMessageEdited a5 = new TdApi.ChatEventMessageEdited();
    a5.oldMessage = createTextMessage(201L, 1L, now - 1500, "Сборка v2.1 выйдет завтра в 12:00.");
    a5.newMessage = createTextMessage(201L, 1L, now - 1200, "Сборка v2.1 выйдет сегодня в 19:00.");
    e5.action = a5;
    list.add(e5);

    // Event 6: Message deleted
    TdApi.ChatEvent e6 = new TdApi.ChatEvent();
    e6.id = 6L;
    e6.date = now - 600;
    e6.memberId = new TdApi.MessageSenderUser(3L);
    TdApi.ChatEventMessageDeleted a6 = new TdApi.ChatEventMessageDeleted();
    a6.message = createTextMessage(202L, 4L, now - 700, "Рекламное сообщение удалено модератором согласно правилам сообщества.");
    e6.action = a6;
    list.add(e6);

    Collections.reverse(list);
    return list;
  }
}
