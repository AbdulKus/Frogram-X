# Проверка push-уведомлений Frogram X

## Что означает регистрация без входящих пакетов

Путь доставки: Telegram → Firebase Cloud Messaging → FirebaseListenerService → PushProcessor → TDLib → уведомление Android.

`Token state: OK` подтверждает получение идентификатора у Firebase. `TDLib registration: N/N registered` подтверждает регистрацию этого идентификатора в Telegram (состояние может быть сохранено локально). Ни один статус не проверяет серверные реквизиты Firebase или фактическую доставку. Счётчик `Packages received` относится к обработке push; сообщения через активное соединение TDLib в него не входят.

При нуле пакетов на нескольких телефонах в первую очередь проверьте настройку отправителя Telegram. Смена каналов уведомлений и постоянное удержание приложения активным не исправляют реквизиты отправителя.

## Две отдельные настройки

| Настройка | Где используется | Что проверять |
| --- | --- | --- |
| `google-services.json` | Android-клиент; secret `GOOGLE_SERVICES_JSON_BASE64` | Пакет `org.frogram.messenger`, `project_id`, `project_number`, `mobilesdk_app_id` |
| JSON сервисного аккаунта Firebase Admin SDK | FCM/push-настройки Android-приложения на `my.telegram.org/apps` | Тот же Firebase-проект, действующий закрытый ключ и права отправки FCM |
| `TELEGRAM_API_ID` и `TELEGRAM_API_HASH` | Сборка и соединение TDLib | Данные именно того Telegram-приложения, где настроен отправитель |

В Firebase Console проверьте включение Firebase Cloud Messaging API (HTTP v1). Получите серверный JSON в **Project settings → Service accounts → Firebase Admin SDK → Generate new private key** и загрузите его в FCM/push-настройки нужного Android-приложения на [my.telegram.org/apps](https://my.telegram.org/apps). Убедитесь, что форма приняла файл без ошибки. Название поля на сайте может отличаться.

Не публикуйте серверный JSON: он содержит закрытый ключ. Загрузка клиентского JSON в GitHub сама по себе не даёт Telegram права отправлять сообщения. Если серверный ключ удалён или отозван, обновите настройки отправителя.

## Проверка после настройки

1. Откройте **Push Services → Copy full diagnostics**. Сверьте `Telegram API ID` с приложением на my.telegram.org, а `project`, `sender` и `app` в `Push configuration` — с Firebase. `sender` соответствует `project_number`.
2. Выполните **Check & re-register push**. Ответ о получении токена означает, что регистрация TDLib запрошена; её успешное завершение отражается отдельно в счётчике и событии `tdlib_device_registered`.
3. Сверните приложение обычной кнопкой Home и погасите экран. Не используйте системную команду «Остановить»: Android блокирует доставку остановленному приложению до следующего запуска.
4. С другого аккаунта отправьте сообщение в личный чат с включёнными уведомлениями. Не читайте это сообщение в другом активном клиенте во время проверки. Дайте приложению перейти в фон; проверьте сразу и после более длительного простоя.
5. Откройте диагностику: должны появиться `push_received`, `push_processor_start`, ненулевая дата последнего push и увеличение `Packages received`.

Если пакеты всё ещё не приходят, независимый тест FCM от владельца Firebase-проекта отделит сбой Telegram-отправителя от сбоя Google Play Services/устройства. Используйте HTTP v1 с `android.priority: HIGH` и data-only сообщением; произвольный тестовый payload может быть отвергнут TDLib, но вход в `push_received` подтверждает доставку до сервиса. Тест с блоком `notification` из Firebase Console может показываться системой в фоне без вызова `onMessageReceived`, поэтому он не эквивалентен data-only push Telegram. Не публикуйте идентификатор установки или токен при таком тесте.

Если независимый data-only push доходит, а Telegram push — нет, повторно проверьте Firebase-проект и серверные реквизиты на my.telegram.org. Если не доходит и независимый тест, проверяйте Google Play Services, сеть и фоновые ограничения устройства. Если `push_received` уже есть, проверяйте последующие события обработки и разрешение на показ уведомлений.

## Firebase Installation ID

Сборки Android 6+ используют `register()` и `onRegistered(installationId)` из Firebase Messaging 25.1.1. Старые варианты используют `getToken()` и `onNewToken()`. В текущем HTTP v1 API Firebase поддерживает FID, в том числе в поле `token` на переходный период. Один факт передачи Installation ID не доказывает ошибку. Не подменяйте FID и FCM-токен вручную и не меняйте `encrypt`: этот флаг управляет шифрованием содержимого Telegram.

Восстановление auto-init при выборе Firebase обеспечивает автоматическую актуализацию регистрации после использования альтернативного push-провайдера. Оно не добавляет опрос сообщений или постоянный сервис.

## Источники

- [Telegram: настройка отправителя и подписка на push](https://core.telegram.org/api/push-updates)
- [Разработчик TDLib: проверка нулевой доставки](https://github.com/tdlib/td/issues/549#issuecomment-491530030)
- [Разработчик TDLib: JSON сервисного аккаунта для HTTP v1](https://github.com/tdlib/td/issues/549#issuecomment-2162847965)
- [Firebase: регистрация и callbacks Android](https://firebase.google.com/docs/cloud-messaging/android/get-started)
- [Firebase HTTP v1: поля token и fid](https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages)
- [Firebase: получение сообщений Android](https://firebase.google.com/docs/cloud-messaging/android/receive)
