package com.moviearchive.ui;

/**
 * Central bilingual ("fa"/"en") string table for the app's UI chrome -
 * button labels, headers, prompts, dialog titles, and the most common
 * status messages. The current language is a static field set once at
 * startup (from AppConfig) and again whenever the user changes it in
 * Settings; changing it triggers a full app restart (see App.restartApp())
 * rather than trying to live-reflow every already-built control, which
 * would be far more fragile.
 *
 * Not every single transient status/error message in the app is routed
 * through here yet - the ones covered are the always-visible chrome
 * (buttons, headers, tab names, dialog titles) plus the most common
 * messages. A handful of rare/specific error strings may still only
 * exist in Persian.
 */
public final class Strings {

    private Strings() {}

    private static volatile String lang = "fa";

    public static void setLanguage(String language) {
        lang = language;
    }

    public static String language() {
        return lang;
    }

    private static boolean fa() {
        return "fa".equals(lang);
    }

    private static String t(String faText, String enText) {
        return fa() ? faText : enText;
    }

    // ================= Main window title =================
    public static String appTitle() { return t("آرشیو فیلم من", "My Movie Archive"); }

    // ================= Toolbar =================
    public static String addScanFolder() { return t("افزودن و اسکن پوشه آرشیو...", "Add & Scan Folder..."); }
    public static String settings() { return t("تنظیمات", "Settings"); }
    public static String reviewQueue() { return t("صف بازبینی", "Review Queue"); }
    public static String reviewQueueWithCount(int n) { return t("صف بازبینی (" + n + ")", "Review Queue (" + n + ")"); }
    public static String searchPrompt() { return t("جستجو در آرشیو (نام، کارگردان، بازیگر، خلاصه)...", "Search archive (title, director, actor, plot)..."); }
    public static String chooseArchiveFolder() { return t("پوشه‌ی آرشیو فیلم را انتخاب کنید", "Choose your movie archive folder"); }

    // ================= View mode / sort (stable keys shown via a localized cell factory) =================
    public static String viewCompact() { return t("نمای فشرده", "Compact"); }
    public static String viewLarge() { return t("پوستر بزرگ", "Large Posters"); }
    public static String viewList() { return t("لیست با جزئیات", "Detailed List"); }
    public static String sortTitle() { return t("عنوان (الفبا)", "Title (A-Z)"); }
    public static String sortYear() { return t("سال (جدیدترین)", "Year (Newest)"); }
    public static String sortRating() { return t("امتیاز (بالاترین)", "Rating (Highest)"); }
    public static String sortAdded() { return t("تاریخ افزودن (جدیدترین)", "Date Added (Newest)"); }

    // ================= Filters sidebar =================
    public static String filtersHeader() { return t("فیلترها", "Filters"); }
    public static String genreHeader() { return t("ژانر", "Genre"); }
    public static String tagsHeader() { return t("تگ‌ها", "Tags"); }
    public static String tagSearchPrompt() { return t("جستجو در تگ‌ها...", "Search tags..."); }
    public static String onlyFavorites() { return t("⭐ فقط علاقه‌مندی‌ها", "⭐ Favorites only"); }
    public static String onlyWatched() { return t("✓ فقط دیده‌شده‌ها", "✓ Watched only"); }
    public static String advancedFilter() { return t("فیلتر پیشرفته", "Advanced Filter"); }
    public static String fromYear() { return t("از سال", "From year"); }
    public static String toYear() { return t("تا سال", "To year"); }
    public static String fromRating() { return t("از امتیاز", "From rating"); }
    public static String toRating() { return t("تا امتیاز", "To rating"); }
    public static String fromRuntime() { return t("از دقیقه", "From min."); }
    public static String toRuntime() { return t("تا دقیقه", "To min."); }
    public static String country() { return t("کشور:", "Country:"); }
    public static String certification() { return t("رده‌بندی سنی:", "Certification:"); }
    public static String clearAllFilters() { return t("پاک کردن همه‌ی فیلترها", "Clear All Filters"); }
    public static String any() { return t("(همه)", "(Any)"); }

    // ================= Detail pane =================
    public static String playMovie() { return t("پخش فیلم", "Play Movie"); }
    public static String trailerOrImdb() { return t("تریلر / صفحه IMDb", "Trailer / IMDb Page"); }
    public static String fixMatch() { return t("اصلاح تطبیق فیلم...", "Fix Movie Match..."); }
    public static String refreshMetadata() { return t("بروزرسانی اطلاعات از TMDB", "Refresh Info from TMDB"); }
    public static String watchedLabel() { return t("دیده‌ام", "Watched"); }
    public static String favoriteLabel() { return t("⭐ علاقه‌مندی", "⭐ Favorite"); }
    public static String selectMoviePrompt() { return t("یک فیلم را از لیست انتخاب کنید", "Select a movie from the list"); }
    public static String directorLabel() { return t("کارگردان:", "Director:"); }
    public static String castLabel() { return t("بازیگران:", "Cast:"); }
    public static String countryFieldLabel() { return t("کشور:", "Country:"); }
    public static String minutesSuffix() { return t("دقیقه", "min"); }
    public static String ratingPrefix() { return t("امتیاز", "Rating"); }
    public static String statusPending() { return t("این فیلم هنوز با TMDB تطبیق داده نشده (کلید API تنظیم نشده بود).", "This movie hasn't been matched with TMDB yet (no API key was set)."); }
    public static String statusNeedsReview() { return t("چند نتیجه‌ی نزدیک در TMDB پیدا شد - لطفاً تطبیق درست را انتخاب کنید.", "Several close TMDB results were found - please pick the right match."); }
    public static String statusNotFound() { return t("در TMDB پیدا نشد - می‌توانید به‌صورت دستی جستجو کنید.", "Not found on TMDB - you can search for it manually."); }

    // ================= Settings dialog =================
    public static String settingsTitle() { return t("تنظیمات", "Settings"); }
    public static String tabGeneral() { return t("عمومی", "General"); }
    public static String tabConnection() { return t("TMDB و پروکسی", "TMDB & Proxy"); }
    public static String tabFolders() { return t("پوشه‌های آرشیو", "Archive Folders"); }
    public static String tabArchive() { return t("آرشیو", "Archive"); }
    public static String save() { return t("ذخیره", "Save"); }
    public static String saved() { return t("ذخیره شد.", "Saved."); }
    public static String languageInfo() { return t(
            "زبان نمایش خلاصه‌داستان، کشور، ژانر، و کل رابط کاربری را انتخاب کنید. تغییر زبان نیاز به راه‌اندازی مجدد اپ دارد.",
            "Choose the display language for the overview, country, genres, and the whole interface. Changing it requires an app restart."); }
    public static String languagePersian() { return t("فارسی", "Persian"); }
    public static String languageEnglish() { return t("English", "English"); }
    public static String restartingNotice() { return t("در حال راه‌اندازی مجدد...", "Restarting..."); }

    public static String tmdbKeyInfo() { return t(
            "برای دریافت پوستر، خلاصه داستان و امتیاز فیلم‌ها، یک TMDB API Key رایگان لازم است.",
            "A free TMDB API key is needed to fetch posters, plot summaries, and ratings."); }
    public static String getKeyLink() { return t("دریافت کلید از themoviedb.org", "Get a key from themoviedb.org"); }
    public static String proxyInfo() { return t(
            "اگر TMDB از شبکه‌ی شما در دسترس نیست (مثلاً به‌خاطر تحریم/فیلترینگ)، یک پروکسی HTTP وارد کنید.\n"
                    + "توجه: باید پورت HTTP ابزار VPN/پروکسی‌تون باشه، نه پورت SOCKS5 (جاوا از SOCKS پشتیبانی نمی‌کنه).",
            "If TMDB isn't reachable from your network, enter an HTTP proxy.\n"
                    + "Note: it must be your VPN/proxy tool's HTTP port, not its SOCKS5 port (Java doesn't support SOCKS)."); }
    public static String useHttpProxy() { return t("استفاده از پروکسی HTTP", "Use HTTP proxy"); }
    public static String address() { return t("آدرس:", "Host:"); }
    public static String port() { return t("پورت:", "Port:"); }
    public static String saveConnectionSettings() { return t("ذخیره تنظیمات اتصال", "Save Connection Settings"); }
    public static String proxyPortMustBeNumber() { return t("پورت پروکسی باید یک عدد باشد.", "Proxy port must be a number."); }
    public static String proxyFieldsRequired() { return t("آدرس و پورت پروکسی را کامل وارد کنید.", "Enter both the proxy host and port."); }

    public static String foldersInfo() { return t(
            "پوشه‌هایی که ثبت می‌کنید، با دکمه‌ی «اسکن همه» یک‌جا بررسی می‌شن (فقط فایل‌های جدید).",
            "Folders you register here get scanned together with \"Scan All\" (new files only)."); }
    public static String addFolder() { return t("افزودن پوشه...", "Add Folder..."); }
    public static String removeSelected() { return t("حذف انتخاب‌شده", "Remove Selected"); }
    public static String scanAllFolders() { return t("اسکن همه‌ی پوشه‌ها", "Scan All Folders"); }
    public static String needTmdbKeyFirst() { return t("ابتدا از «تنظیمات» یک TMDB API Key وارد کنید.", "First enter a TMDB API key in Settings."); }

    public static String exportImportInfo() { return t(
            "Export / Import (JSON) - خوانا و قابل‌دیف است، ولی فقط مسیر لوکال پوستر رو ذخیره می‌کنه.",
            "Export / Import (JSON) - human-readable and diffable, but only stores the local poster path."); }
    public static String exportJson() { return t("Export به JSON", "Export to JSON"); }
    public static String importJson() { return t("Import از JSON", "Import from JSON"); }
    public static String backupInfo() { return t(
            "بکاپ کامل - دیتابیس و همه‌ی پوسترها را در یک فایل zip می‌ذاره؛ بازگردانی‌اش نیازی به دریافت دوباره از TMDB نداره.",
            "Full backup - puts the database and every poster into one zip file; restoring it needs no TMDB re-fetching."); }
    public static String fullBackupExport() { return t("بکاپ کامل (Export)", "Full Backup (Export)"); }
    public static String fullBackupRestore() { return t("بازگردانی از بکاپ کامل", "Restore from Full Backup"); }
    public static String refreshAllInfo() { return t(
            "بروزرسانی متادیتا - برای فیلم‌هایی که قبلاً تطبیق پیدا کردن، اطلاعات رو دوباره از TMDB می‌گیره (پوستر از کش استفاده می‌شه، دوباره دانلود نمی‌شه).",
            "Refresh metadata - re-fetches info from TMDB for already-matched movies (posters are reused from cache, not re-downloaded)."); }
    public static String refreshAllMatched() { return t("بروزرسانی متادیتای همه‌ی فیلم‌های تطبیق‌یافته", "Refresh Metadata for All Matched Movies"); }
    public static String restoreConfirmTitle() { return t("بازگردانی کامل", "Full Restore"); }
    public static String restoreConfirmMessage() { return t(
            "این کار آرشیو و پوسترهای فعلی را با محتوای بکاپ جایگزین می‌کند و قابل بازگشت نیست. ادامه می‌دهید؟",
            "This replaces your current archive and posters with the backup's contents and cannot be undone. Continue?"); }
    public static String rematchRunningStopFirst() { return t(
            "یک تطبیق دسته‌جمعی در حال اجراست - اول آن را متوقف کنید.",
            "A bulk re-match is currently running - stop it first."); }

    // ================= Review dialog =================
    public static String fixMatchTitle(String movieName) { return t("اصلاح تطبیق: " + movieName, "Fix Match: " + movieName); }
    public static String searchTmdbPrompt() { return t("عنوان فیلم برای جستجو در TMDB", "Movie title to search on TMDB"); }
    public static String search() { return t("جستجو", "Search"); }
    public static String selectThisMovie() { return t("انتخاب این فیلم", "Select This Movie"); }
    public static String skipKeepNoMetadata() { return t("رد شدن (بدون متادیتا نگه دار)", "Skip (keep without metadata)"); }
    public static String searching() { return t("در حال جستجو...", "Searching..."); }
    public static String noResultsFound() { return t("نتیجه‌ای پیدا نشد.", "No results found."); }
    public static String resultsFoundCount(int n) { return t(n + " نتیجه پیدا شد.", n + " results found."); }
    public static String fetchingInfo() { return t("در حال دریافت اطلاعات...", "Fetching info..."); }
    public static String errorPrefix(String msg) { return t("خطا: " + msg, "Error: " + msg); }
    public static String fileOpenError(String msg) { return t("خطا در باز کردن فایل: " + msg, "Error opening file: " + msg); }

    // ================= Review queue dialog =================
    public static String reviewQueueTitle() { return t("صف بازبینی", "Review Queue"); }
    public static String close() { return t("بستن", "Close"); }
    public static String fixEllipsis() { return t("اصلاح...", "Fix..."); }
    public static String rematchAll() { return t("تطبیق خودکار مجدد همه", "Re-match All Automatically"); }
    public static String stop() { return t("توقف", "Stop"); }

    // ================= MovieListRow indicators =================
    public static String favoriteIndicator() { return t("★ علاقه‌مندی", "★ Favorite"); }
    public static String watchedIndicator() { return t("✓ دیده‌شده", "✓ Watched"); }
    public static String needsReviewIndicator() { return t("! نیاز به بررسی", "! Needs review"); }

    // ================= Dynamic status/error messages (main window) =================
    public static String scanSummary(int found, int cached, int matched, int review, int noKey) {
        return fa()
                ? String.format("پایان اسکن: %d فیلم پیدا شد، %d قبلاً در آرشیو بود، %d تطبیق خودکار، %d نیاز به بررسی، %d بدون کلید API",
                        found, cached, matched, review, noKey)
                : String.format("Scan finished: %d found, %d already in archive, %d auto-matched, %d need review, %d without API key",
                        found, cached, matched, review, noKey);
    }
    public static String scanError(String msg) { return t("خطا در اسکن: " + msg, "Scan error: " + msg); }
    public static String refreshingMovie(String title) { return t("در حال بروزرسانی: " + title, "Refreshing: " + title); }
    public static String refreshedMovie(String title) { return t("بروزرسانی شد: " + title, "Refreshed: " + title); }
    public static String refreshError(String msg) { return t("خطا در بروزرسانی: " + msg, "Refresh error: " + msg); }
    public static String loadError(String msg) { return t("خطا در بارگذاری آرشیو: " + msg, "Error loading archive: " + msg); }
    public static String saveError(String msg) { return t("خطا در ذخیره: " + msg, "Save error: " + msg); }
    public static String openReviewQueueError(String msg) { return t("خطا در باز کردن صف بازبینی: " + msg, "Error opening review queue: " + msg); }
    public static String restartFailed(String msg) { return t("راه‌اندازی مجدد ناموفق بود: " + msg, "Restart failed: " + msg); }
}
