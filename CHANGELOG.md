# Changelog

> Poznámka: mezi v0.3.3 a v0.8.0 se tenhle soubor neudržoval. Co se dělo mezitím, je
> vidět v historii commitů a v popisech jednotlivých vydání na GitHubu; zpětně to sem
> nedopisuju, abych si nevymýšlel.

## v0.8.5

### Upozornění při aktualizaci
- Uložené překlady se po instalaci přepočítají — obrys bublin se nově počítá jinak a staré záznamy nesou ten původní, přetékající.

### Opravené chyby
- **Bublina přemalovávala text bubliny sousední.** Kaskádová replika bývá nakreslená jako dvě *překrývající se* bublinky, které tvoří jednu spojitou bílou plochu. Hledání obrysu se přes to místo přelilo do druhého laloku, takže každá bublina si myslela, že jí patří plocha obou — a ta poslední vykreslená přemalovala text těch ostatních. Zmizel tak i text, který se vůbec nepřeložil: místo něj zůstala prázdná bílá plocha.

  Změřeno na zařízení: bez opravy dostaly všechny tři textové bloky na testovací stránce **naprosto stejný obrys** (celý balón), po opravě má každý svůj vlastní úsek.

  Při té příležitosti se potvrdilo, že rozpoznávání textu horní bublinu **najde** — chyba byla čistě ve vykreslování, ne v OCR ani v překladu.

## v0.8.4

### Upozornění při aktualizaci
- Uložené překlady se po instalaci přepočítají. Změnilo se, co překladač o bublinách ví (viz níž), takže staré výsledky by tu opravu neobsahovaly. Nic se neztratí, jen první otevření kapitoly bude pomalejší.

### Kvalita překladu
- **Věta rozdělená do dvou bublin se překládá jako celek.** Překladač dosud viděl jen plochý seznam textů a neměl jak poznat, že dvě bubliny tvoří jednu repliku — kaskádový dialog (úvodní citoslovce nahoře, zbytek dole) se tak překládal po kouscích a návaznost se ztrácela. Nově se souvislost spočítá z rozmístění bublin a interpunkce a překladač ji dostane jako zadání: přelož jako celek, ale **rozděl zpátky přesně tak, jak byl rozdělený originál**. Text se nikdy nepřesouvá mezi bublinami, horní zůstává nahoře a spodní dole.
- **Tradiční čínština se čte zprava doleva.** Směr rozhodovala jen japonština, takže tchajwanské a hongkongské komiksy dostávaly bubliny seřazené obráceně a překladač četl repliky pozpátku. Zjednodušené čínštiny se to netýká — ta se čte zleva doprava. Projeví se, jen když si zdrojový jazyk vyberete ručně.

## v0.8.3

### Upozornění při aktualizaci
- Uložené překlady se po instalaci přepočítají. Zdrojový jazyk je součástí jejich klíče, a ten se teď mění (viz níž) — nic se neztratí, jen první otevření kapitoly bude pomalejší a sáhne to na denní limit překladů.

### Opravené chyby
- **Výchozí zdrojový jazyk byl „English", takže japonská manga se nepřeložila vůbec.** Na japonskou, korejskou i čínskou stránku se pouštěl latinkový rozpoznávač, který na nich nenajde nic — naměřeno doslova nula znaků. Výsledek: prázdný překlad bez jediného vysvětlení. Nově je výchozí „Auto" a rozpoznávač se vybírá podle toho, co na stránce doopravdy je.

### Vzhled
- **Text ležící přímo na kresbě už se nepřekrývá jednolitou plochou.** Dosud se přes celý rámeček natáhla jedna navzorkovaná barva — odtud hnědé placky přes barevné kresby a tmavé skvrny přes obličeje. Nově se zakryjí jen tahy původního písma a každý zakrytý bod se dopočítá z okolí, takže kresba mezi písmeny zůstane vidět. U běžných bublin se nic nemění, tam byla dosavadní výplň k nerozeznání od originálu.

## v0.8.2

### Opravené chyby
- **Sdílení QR kódu bylo opravené jen napůl.** Ve v0.8.0 se dialog sdílení začal otevírat, ale příjemce obrázek nesměl otevřít — místo náhledu zůstalo prázdno a sdílení do řady aplikací selhalo. Nalezeno až při zkoušce na emulátoru.

## v0.8.1

### Upozornění při aktualizaci
- Uložené překlady se po instalaci zahodí a spočítají znovu (změnila se rozpoznávací a klasifikační logika). První otevření kapitoly bude pomalejší a sáhne to na denní limit překladů.

### Opravené chyby
- **Zdrojový jazyk „Auto" ve skutečnosti znamenal latinku.** Byla to první nabízená možnost, ale rozpoznávání textu pro ni nemělo vlastní větev a spadlo na latinkový model — kdo si „Auto" vybral a otevřel japonskou, korejskou nebo čínskou mangu, dostal nesmysl nebo nic. Bubliny se navíc seřadily zleva doprava, takže překladač četl repliky pozpátku. Nově se rozpoznávač vybírá podle toho, co na stránce opravdu je.
- **Běžný dialog mohl zmizet jako „vodoznak".** Tři repliky, kde každá jen prodlužovala předchozí („HELP" / „HELP ME" / „HELP ME NOW"), se označily za nastampovaný vodoznak a vůbec se nepřeložily.
- **Krátké repliky v neanglických komiksech se ztrácely.** Pravidlo „krátký text velkými písmeny = zvukový efekt" mělo pojistku jen pro angličtinu, takže třeba španělské „VAMOS" propadlo jako zvuk a zůstalo nepřeložené.

### Data
- **Cache přeložených novel se nikdy nezneplatnila ani neuklízela.** Po opravě překladu zůstávala stará verze napořád, tabulka rostla bez omezení a tlačítko „smazat cache překladů" se jí vůbec nedotklo.
- Počet uložených překladů v Úložišti teď zahrnuje i novely — dřív ukazoval míň, než kolik toho appka doopravdy držela.

## v0.8.0

### Upozornění při aktualizaci
- Uložené překlady se po instalaci zahodí a spočítají znovu (změnil se překladový řetězec). První otevření kapitoly bude pomalejší a sáhne to na denní limit překladů.

### Opravené chyby
- **Sdílení QR kódu nedělalo vůbec nic.** Tlačítko „Sdílet" pokaždé selhalo a mlčelo o tom, protože v manifestu chyběl FileProvider.
- **Reset hesla hlásil selhání jako úspěch.** Když se e-mail nepodařilo odeslat, vyskočila hláška „Chyba: Email pro reset odeslán". Teď se ukáže skutečná příčina.
- **Časovač spánku držel v paměti celou obrazovku appky** po celou dobu odpočtu a po otočení displeje přestal fungovat.
- **Obrazovka Statistik byla nedosažitelná** — 575 řádků hotového kódu, na který nevedla žádná cesta. Nově se otevírá klepnutím na statistický řádek v knihovně.
- **Tlačítko zrušení časovače spánku** nereagovalo na to, že časovač mezitím běží.
- **Mrtvý nebo blokující zdroj** už nevypadá, jako by prostě nic nenašel.
- Doplněno 51 chybějících překladů — anglické rozhraní na několika místech ukazovalo češtinu.
- Opraven únik síťového spojení při neúspěšném přihlášení ke Kitsu a MangaUpdates.

### Data a soukromí
- **Obnova ze zálohy teď běží celá najednou.** Dřív se zapisovala po částech, takže chyba uprostřed nechala knihovnu rozečtenou. Nově se obnoví buď všechno, nebo nic.
- **Záloha z novější verze appky se odmítne** místo toho, aby se naslepo naparsovala jako ta současná.
- **Inkognito režim už nezapisuje nic.** Dřív vynechal jen historii a hlášení trackerům, ale kapitolu stejně označil jako přečtenou, posunul „naposledy čteno" a započítal čas i stránky do Statistik.

### Vzhled a texty
- **Počty se konečně skloňují.** Místo „1 kapitol", „3 kapitol" nebo „Přidat 1 mang do kategorie" appka používá správné tvary ve všech čtyřech jazycích.

### Pod kapotou
- **Release build šel po dlouhé době znovu sestavit** — kvůli chybě v R8 padal každý pokus a vydávalo se ve skutečnosti ladicí APK. Tohle je první pořádně minifikované vydání: **51,7 MB místo 68,3 MB**.
- Zapnuto hlášení chyb — zachycené výjimky už nemizí beze stopy.
- Knihovna pro šifrované úložiště tracker tokenů povýšena z alpha na stabilní verzi.
- Přibyly první testy ViewModelů; celkem jich projekt má 578.

## v0.3.3

### Nové funkce
- Stahování aktualizace teď zobrazuje celoobrazovkovou animaci - stylizovaný "skleněný květ" se otevírá a fialové srdce uprostřed sílí podle skutečného procenta stažení, místo prostého progress baru v Nastavení. Lze schovat tlačítkem X (stahování běží dál na pozadí).

## v0.3.2

### Opravené chyby
- Oprava zaseklého dialogu "ověření, že nejsi robot", který se donekonečna opakoval, pokud web appku trvale zablokoval (ne řešitelná výzva, rovnou "Sorry, you have been blocked"). Appka si teď po neúspěchu na 10 minut pamatuje, že daný web je zablokovaný, a nezobrazuje dialog znovu pro každý další obrázek/stránku - týká se všech zdrojů v appce. Přidáno i viditelné tlačítko "Zavřít" do dialogu.

## v0.3.1

### Opravené chyby
- Přeložený text v čtečce se už nepřekrýval sám se sebou ani nepřetékal mimo bublinu - OCR teď slučuje textové řádky do bublin přesněji, box pro překlad dostane jen tolik místa, kolik je volné k nejbližší sousední bublině, a velikost písma se automaticky zmenší, aby se text vešel. Styl překladu změněn z černého boxu s bílým textem na bílý štítek s tmavým textem (méně ruší kresbu).

### Nové zdroje
- Přidány Manga18fx, Hentai20.io a Webtoon XYZ.

## v0.2.2

### Bezpečnost
- Odstraněn nevyužívaný exportovaný deep link `jiyu://anilist` (implicit-flow OAuth token by přes něj teoreticky mohl zachytit jiný nainstalovaný app se stejným schématem).

### Opravené chyby
- Světlý režim: opraven hardcoded tmavý horní gradient, kvůli kterému byl v light theme nečitelný horní panel.
- Opraven kontrast textu v tmavých dialozích/sheetech (obálky, hromadné akce v knihovně, filtr v Procházet, potvrzovací dialogy), které v light theme používaly na pevně tmavém pozadí barvy reagující na motiv.
- Opraveno přetékání textu v horní liště čtečky (název kapitoly se ořezával po pár znacích kvůli přeplněné řadě ikon).
- Sjednoceny nekonzistentní (mix anglicky/česky) popisy zdrojů v katalogu zdrojů.
- **Americké komiksy a Light Novel zdroje**: většina vestavěných zdrojů byla dlouhodobě nefunkční (mrtvé domény, ukončené služby, Cloudflare/JS ochrana). Odstraněno 12 mrtvých comic zdrojů (ReadComicOnline, ReadAllComics, ViewComic, XoxoComics, ZipComic, ComicPunch, GoComics, GlobalComix, ComicKingdom, ComicExtra, ReadComicsOnline, SuperHeroComics) a 3 mrtvé novel zdroje (BoxNovel, LightNovelWorld, LightNovelPub). Nahrazeno funkčními alternativami (ReadFreeComicsOnline, FreeWebNovel) a opraveny zbylé rozbité zdroje (GetComics, ComicBookPlus, NovelFull).

### Nové funkce
- Plná internacionalizace uživatelského rozhraní (čeština/angličtina/francouzština/španělština) - předtím byla externalizována jen malá část textů.

## v0.2.1

### Bezpečnost
- Odstraněn GROQ_API_KEY z klientské appky (dal se vytáhnout přímo z veřejného APK). AI překlad teď jde přes server-side Supabase Edge Function proxy (`translate-proxy`), klíč zůstává jen na serveru.
- Přidán rate-limiting proti zneužití proxy (denní limit počtu požadavků a znaků na uživatele).
- Zrušena funkce AI shrnutí kapitol / AI analýza mangy (aby nebylo nutné vystavovat další klíč přes druhé proxy).

### Opravené chyby
- Oprava pádu při AI překladu v čtečce (`NoSuchMethodError` na `JSONObject.put(String, float)`).
- Oprava pádu při zálohování knihovny, pokud obsahovala alespoň jednu kapitolu (stejná příčina jako výše).
- Oprava kontroly aktualizací, která nenacházela nové verze.

### Nové funkce
- Aktualizace se nyní stahují a instalují přímo v appce (systémový DownloadManager + notifikace), místo otevírání GitHubu v prohlížeči.
- Nová ikona aplikace.
- Nastavení kompletně přestavěno do stylu Kotatsu: 10 kategorií na hlavní stránce, každá se otevírá do vlastní podstránky ("O aplikaci" jako poslední).

## v0.2.0

- Nová Knihovna dashboard, redesign Procházet.
- Oprava pádu při AI překladu.
- Oprava únikajícího API klíče (první průchod, dořešeno v v0.2.1).
