# Установка и запуск проекта Telegram sale_bot_VGTU

## Требования

- Java 17+
- Maven
- MongoDB Atlas (или локальный MongoDB)
- Аккаунт Telegram
- MongoDB Compass (опционально — для просмотра логов)
- IDE: IntelliJ IDEA / Eclipse / VS Code

---

## Установка и запуск

### 1. Установите IntelliJ IDEA (рекомендуемая IDE для Java)

### Где скачать

1. Перейдите на официальный сайт:  
   [https://www.jetbrains.com/idea/download](https://www.jetbrains.com/idea/download)

2. Выберите версию:
    - **Community Edition** — бесплатная и подходит для данного проекта
    - **Ultimate Edition** — платная (не требуется)

3. Нажмите **Download** и установите приложение, следуя инструкции установщика.

### 2. Как открыть проект в IntelliJ IDEA

1. Запустите IntelliJ IDEA
2. Нажмите **"Open"**
3. Выберите папку с проектом (где находится `pom.xml`)
4. IntelliJ автоматически определит, что это Maven-проект, и подгрузит зависимости
5. Подождите, пока завершится индексация (внизу IDE будет прогресс-бар)

### 3. Как запустить проект из IDE

1. Откройте файл:  
   `src/main/java/org/example/Main.java`

2. Кликните правой кнопкой мыши по методу `main()` и выберите:  
   **Run 'Main.main()'**

3. В консоли появится сообщение:


### 4. Клонируйте или скачайте проект:

*git clone https://github.com/Maryssika/sale_bot_VGTU.git

cd sale_bot_VGTU*

### 5. Настройка MongoDB
- Создайте кластер MongoDB в MongoDB Atlas
- Получите строку подключения
- В файле MongoDBService.java укажите свою строку подключения

### 6. Настройка Telegram-бота
- Перейдите в Telegram и найдите бота @BotFather
- Выполни команду /newbot
- Укажите имя и username бота
- Скопируйте токен и вставьте в TelegramBot.java:
  *public String getBotToken() {
  return "ВАШ_ТОКЕН_ОТ_BOTFATHER";
  }*
### 7. Запуск
- запустите класс Main.java через IDE.
- После запуска в консоли появится: Bot started successfully!
