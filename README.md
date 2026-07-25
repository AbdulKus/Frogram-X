<p align="center">
  <img src="images/frogram-x-icon.png" width="180" alt="Frogram X">
</p>

<h1 align="center">Frogram X</h1>

<p align="center">
  Экспериментальный независимый форк Telegram X для Android на базе TDLib.
</p>

## Что изменено

- отдельное имя и идентификатор приложения `org.frogram.messenger` — Frogram X можно установить рядом с оригинальным Telegram X;
- поддержка топиков: отдельная история, непрочитанные и закреплённые сообщения, создание, редактирование, закрытие и удаление при наличии прав;

Frogram X находится в разработке. Перед обновлением рекомендуется сохранять резервную копию важных данных и ключа подписи APK.

## Скачать сборку

Откройте [GitHub Actions](https://github.com/AbdulKus/Xfork/actions/workflows/android.yml), выберите последнюю успешную сборку **Build Frogram X** и скачайте артефакт `Frogram-X-…`.

Артефакты хранятся 30 дней. Рядом с APK публикуется файл `SHA256SUMS.txt` для проверки контрольной суммы.

## Автоматическая сборка

Workflow `.github/workflows/android.yml` запускается при изменениях в `main`, `feature/**`, `fix/**` и вручную через `workflow_dispatch`.

Для постоянной подписи APK добавьте в **Settings → Secrets and variables → Actions**:

- `TELEGRAM_API_ID`
- `TELEGRAM_API_HASH`
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Если секреты подписи не заданы, Actions создаст временный ключ. Такой APK нельзя будет установить как обновление поверх сборки, подписанной другим ключом. Храните исходный keystore и пароли в нескольких защищённых резервных копиях.

## Локальная сборка

Требуются Git с LFS, Java 21, Android SDK/NDK и стандартные инструменты сборки Linux.

```bash
git clone --recursive https://github.com/AbdulKus/Xfork.git
cd Xfork
scripts/setup.sh
./gradlew assembleLatestArm64Debug
```

Telegram API ID и hash можно получить на [my.telegram.org](https://my.telegram.org). Дополнительная информация об исходной архитектуре и зависимостях доступна в [руководстве разработчика](docs/GUIDE.md) и [списке сторонних компонентов](docs/THIRDPARTY.md).

## Лицензия и происхождение

Проект основан на открытом исходном коде [Telegram X](https://github.com/TGX-Android/Telegram-X) и распространяется по лицензии [GNU GPLv3](LICENSE).

Frogram X не является официальным приложением Telegram и не связан с Telegram FZ-LLC или разработчиками Telegram X. Названия и товарные знаки Telegram и Telegram X принадлежат их правообладателям.
