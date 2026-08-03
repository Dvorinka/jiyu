# Changelog

> Poznámka: mezi v0.3.3 a v0.8.0 se tenhle soubor neudržoval. Co se dělo mezitím, je
> vidět v historii commitů a v popisech jednotlivých vydání na GitHubu; zpětně to sem
> nedopisuju, abych si nevymýšlel.

## v1.0.7

Oprava regrese z v1.0.6. **Pipeline se mění (16 → 17), stránky se přeloží znovu** — chybný
příznak se ukládá do cache, takže bez toho by placky na už přeložených stránkách zůstaly.

### Plná výplň se rozlila přes kresbu
Popisek „BITVA U SEKIGAHARY" dostal přes vodovkovou bitevní scénu modrou placku, panely
„DUP"/„TROMP" růžovou. Do v1.0.5 se tam kresba dopočítávala a bylo to bez poznání.

Způsobila to změna z v1.0.6, kterou jsem zavedl kvůli zbytkům originálu v bublinách:
jednolitost pozadí přestala hlídat největší odchylku vzorku a začala brát 85. percentil.
Jenže ta bitevní scéna je barevně *docela* jednotná — většina prstence padne do tolerance
a mimo ni je jen menšina (kmen stromu, tmavý terén pod popiskem). Prošla tedy jako
„jednolité pozadí" a dostala plnou výplň místo záplaty.

Rozhoduje zase největší odchylka, tedy stav z v1.0.5.

Podstatné je, proč to nešlo jen doladit jinou mezí: z pouhých vzorků je „pár odlehlých
tmavých hodnot" **nerozlišitelné** mezi tahem písmene a tmavým detailem kresby. Percentil
tedy nemůže být ten mechanismus, ať dostane jakýkoliv práh.

**Vrací se tím i ústupek z v1.0.5:** v bublině může pod překladem zůstat drobný zbytek
originálu. Placka přes kresbu je horší, takže to je vědomá volba, ne přehlédnutí. Řešit se
to bude tím, kde se vzorkuje prstenec kolem textu — ne tolerancí.

808 unit testů, 0 varování.

## v1.0.6

Samé opravy překladu, všechny z nahlášených stránek Vagabonda. **Pipeline se mění
(PIPELINE_VERSION 15 → 16), takže se už přeložené stránky přeloží znovu** — jinak by
nová pravidla na staré cache nebyla vidět.

### Klasifikace zvuků polykala dialogy
Věty jako „POSLEDNÍ" nebo „SKONČILA." se braly za kreslený zvuk (SFX) a nechávaly se
nepřeložené. Vinou toho bylo pravidlo „vše velkými písmeny je zvuk" plus ruční seznam
výjimek, který nešlo doplnit tak, aby pokryl každé krátké slovo v jazyce.

Rozhoduje teď uzavřený seznam skutečných zvuků a dva signály, které žádný seznam
nepotřebují: latinské slovo bez jediné samohlásky, a krátký text nakreslený přímo na
kresbě (mimo bublinu). Seznam zvuků navíc snese protažené varianty — „KRAAASH"
i „KRASH" se spárují se stejnou položkou.

### Rozdělená slova s jedním písmenem na řádku
„POSLEDN" / „Í" — model směl dělící místa navrhovat kamkoliv a appka je přebírala bez
kontroly. Nově se každý úsek mezi dělítky počítá na písmena a s méně než dvěma se
dělení zahodí.

### Náhradní překladač psal bez kontextu
Když hlavní poskytovatel odpadl, záložní dostal holé věty bez názvu díla i bez
předchozích replik — odtud nesouvislé a doslovné překlady uprostřed dialogu. Kontext
se teď posílá i tudy.

> Vyžaduje nasazení edge funkce, jinak ji appka posílá a proxy zahazuje.

### Bílá bublina se brala za pestrou kresbu
Pod přeloženým textem zůstávaly zbytky originálu — osamocená tečka, drobné artefakty.
Prstenec kolem textu se vzorkuje kousek od OCR boxu a ten občas ořízne kraj písmene,
takže pár vzorků padlo rovnou na černý tah. Podmínka přitom brala největší odchylku,
takže jediný takový vzorek přehodil celou bublinu na „pestrá kresba" a ta pak dostala
záplatu místo plné výplně.

Rozhoduje teď 85. percentil: hrst vzorků na písmenu verdikt neovlivní. Ověřeno na
zařízení, že text na SKUTEČNĚ pestré kresbě dál vychází správně — tolerance neoslabila
rozpoznání, kvůli kterému záplata vznikla.

### Popisky na kresbě zůstávaly čitelné i po překladu
Maska záplaty se ořezávala přesně na OCR box, jenže ten je jen aproximace otisku písma —
ML Kit ho běžně vede kus uvnitř skutečných tahů. Co přesáhlo, zůstalo nedotčené: lem
kolem písmen, spodky dotahů, tečky na hranici. Oblast se teď před ořezáním rozšíří
o rezervu odvozenou z výšky písma.

808 unit testů, 0 varování.

## v1.0.5

Na prekladu se nic nemeni - prelozene stranky zustavaji v cache.

### Prepinac "Je mi 18 a vice" zdroje pro dospele neodemykal
Prepinac psal jen priznak potvrzeneho veku, kdezto seznam zdroju se ridi uplne jinym nastavenim (Nastaveni > Zdroje). A byl zapojeny jen JEDNIM smerem: pri vypnuti zdroje schoval, pri zapnuti neudelal nic - prestoze pod nim stoji "Odemyka zdroje s obsahem pro dospele". Tykalo se to i zdroju se smisenym obsahem.

Bylo to dusledkem drivejsi zmeny, kdy se vychozi viditelnost prehodila z "zapnuto" na "vypnuto"; do te doby to bylo zapnute samo a rozpojeny smer nebyl videt.

Opraveno na obou stranach: prepinac ted nastavuje obojí, a kdo ma plnoletost potvrzenou z drivejska a viditelnost nikdy vyslovne nenastavenou, uvidi zdroje hned po aktualizaci bez sahani na cokoliv. Kdo si je vypnul sam, ma svou volbu zachovanou.

### Hlavicky uz pri scrollovani neplavou s obsahem
Historie, Aktualizace, Muj seznam a Nastaveni - stejne jako driv Prochazet a Knihovna. Hlavicka stala mimo scrollovanou oblast, takze zustavala viset nahore a obsah jezdil pod ni.

775 unit testu, 0 varovani.

## v1.0.4

Na prekladu samotnem se nic nemeni - prelozene stranky zustavaji v cache.

### Preklad kapitol na pozadi
Preklad se do ted spoustel vyhradne ze ctecky, a to ve viewModelScope - tedy ve scope svazanem se zivotem te obrazovky. Odejdi ze ctecky nebo zavri appku a preklad se zrusil uprostred. Zadne prekladani na pozadi v appce neexistovalo, jen se tak tvarilo.

Nove bezi ve WorkManageru s popredovou notifikaci. Overeno: po spusteni a zavreni appky notifikace zustava, worker vola API a preklad pokracuje. Do cache se behem testu se zavrenou appkou ulozilo 45 prelozenych stranek.

### Prelozit kapitoly dopredu
V detailu mangy: prekryvne menu kapitol -> "Prelozit dopredu..." -> 1/3/5/10 kapitol. Fronta je zamerne sekvencni: preklad nebrzdi rychlost site jako stahovani, ale znakova kvota, takze pet kapitol najednou by ji vycerpalo petkrat rychleji a stalo by frontu na tentyz upstream.

Bere se od nejnizsiho cisla NEPRECTENYCH kapitol. Seznam na detailu je bezne otoceny (nejnovejsi nahore), takze bez razeni podle cisla by "5 kapitol dopredu" prelozilo pet nejnovejsich - presny opak toho, co chce ctenar.

### Groq obcas odpovedel prozou misto JSONu
Vynuceny JSON rezim dostavali dva provideri ze tri: Gemini responseMimeType, OpenRouter json_schema, Groq nic. Model tedy smel odpovedet prozou ("Preklady...") a appka celou davku zahodila na vyjimce vcetne znaku, ktere za to volani upstream uz odecetl.

Groq ted dostava response_format json_object (vyzaduje nasazeni edge funkce, uz provedeno) a parseResponse navic vyloupne JSON i z okolniho textu.

768 unit testu (+11), 0 varovani.

## v1.0.3

Jen ovladani, na prekladu se nic nemeni - prelozene stranky zustavaji v cache.

### Hlavicky uz pri scrollovani neplavou s obsahem
Na Prochazet i na Knihovne stala hlavicka (nadpis, hledani, filtry) mimo scrollovanou oblast a zustavala viset nahore. Zabirala skoro tretinu obrazovky porad, i pri scrollovani hluboko v seznamu. Ted odjede s obsahem: v Prochazet je po odscrolovani videt 7 rad zdroju misto 4,5.

### "Zobrazit vse" na Knihovne konecne neco dela
Sipka i napis vypadaly jako odkaz, ale byl to obycejny text bez jakehokoli kliknuti - nikdy to nic nedelalo. Otevira se ted obrazovka s celou sekci (Pokracovat ve cteni / Nedavno pridane / Dokoncene) v mrizce po trech misto vodorovneho posuvniku, ve kterem se u vic titulu neda nic najit. U rozecetenych titulu vede klepnuti rovnou do posledni kapitoly.

757 unit testu, 0 varovani.

## v1.0.2

Prelozene stranky se pri prvnim otevreni prelozi znovu (PIPELINE_VERSION 13 -> 15).

### Vzorovane pozadi bubliny uz neprekryje bila nalepka
Bubliny, ktere maji uvnitr jemnou texturu, dostavaly pres sebe bilou plochu, ktera pokryla jen stred - po okrajich prosvitala puvodni textura a vypadalo to jako nalepka nalepena pres kresbu. Obrys balonku hledame vylevanim barvy a texturni cary se pro nej chovaji jako stena, takze se vylevani zastavi driv, nez dojde k okraji. Nove rozhoduje, jestli JE pozadi jedne barvy, ne jestli se nasel nejaky obrys: kdyz jednolite neni, zakryji se jen tahy pismen a vzorek kolem prezije.

### Preklad vi, odkud dilo je
Typ obsahu se modelu posilal jen jako nalepka v zavorce a co z ni plyne si musel domyslet sam - u manhwy si typicky domyslel japonska honorifika, prestoze "hyung" a "senpai" nejsou zamenitelne. Kazdy typ ma ted vlastni pravidlo pro osloveni a prepis jmen.

### Preklad navazuje na to, co uz zaznelo
Uvnitr jedne davky mel model kontext vzdycky, ale na jeji hranici zacinal s cistym stolem, takze se uprostred rozhovoru mohlo prehodit tykani/vykani nebo osloveni postavy. K dalsi davce se ted pribali ocasek uz prelozenych replik. Plati to i pri cteni stranku po strance.

### Svisle sazena japonstina
Cela stranka se slevala do JEDNOHO bloku s promichanym textem. ML Kit vraci cely sloupec jako jeden "radek" a stare pravidlo porovnavalo mezeru mezi sloupci s vyskou sloupce - to je vzdalenost pres pul stranky, takze se slily i bubliny 350 px od sebe. Sloupce maji ted vlastni pravidlo a skladaji se zprava doleva.

### Zmereno a zamitnuto
Predzpracovani obrazku pred OCR (binarizace, roztazeni kontrastu, zvetseni) i pouziti OCR confidence jako varovani. Ani jedno nepomohlo natolik, aby stalo za svou cenu - cisla jsou v repozitari u prislusnych sond, aby se to nezkouselo znovu od nuly.

757 unit testu (+32), 0 varovani.

## v1.0.1

Prelozene stranky se pri prvnim otevreni prelozi znovu (PIPELINE_VERSION 12 -> 13).

### Kvalita prekladu - tri doblozene priciny
- **Slova delena pomlckou na konci radku se spoji.** Bublina "EVERY-" / "ONE DON'T SCATTER, STAY TOGETHER!" dorazila k modelu jako rozsypany zacatek vety a v prekladu z ni vypadl zapor: "VSICHNI SE ROZPTYLEJTE, ZUSTAVEJTE SPOLU!" - veta, ktera si odporuje sama v sobe. Pomlcka se nemaze, jen se odstrani zalomeni za ni, takze skutecny spojovnik ("well-known") zustane nedotceny.
- **Do glosare uz nejde ulozit cokoliv.** Plnil se automaticky z toho, co model vratil, BEZ jedine kontroly - a v promptu byl oznaceny jako zavazny. Stacilo, aby si tam jednou zapsal nesmysl, a vnucoval si ho ve vsech dalsich kapitolach. Odtud "ZAVRI PANU" misto "drz hubu"; slovo "mouth" pritom zadny druhy vyznam nema. Nove projde jen to, co vypada jako jmeno, ne bezne slovo ani cela veta.
- **Prompt ma pet pravidel uplne nahore** (zapor se nesmi ztratit, veta si nesmi odporovat, idiom nedoslova) a **zaverecnou kontrolu** pred sestavenim odpovedi. Glosar uz neni nadrazeny smyslu vety.

Zadne z toho nestoji jedine API volani navic.

725 unit testu (+18), 0 varovani.

## v1.0.0

Prvni plna verze. Prelozene stranky se pri prvnim otevreni prelozi znovu (PIPELINE_VERSION 11 -> 12, viz v0.9.1).

### Preklad
- **Rucni oprava bubliny.** Dlouhy stisk -> prepsat text -> ulozit. Preklad stoji na free modelech, ktere obcas selzou, a do ted s tim neslo nic delat. Opravy zijou ve vlastni tabulce, takze prezijou i prepocet po zvednuti verze pipeline; bublina se pozna podle puvodniho textu, ne podle poradi. Prazdne pole opravu zrusi.
- Cerna placka pres pul panelu v miste vodoznaku (v0.9.1), rozmazany cizi text pres preklad (v0.8.9), necentrovany text v bublinach s ocaskem (v0.8.8), neprelozeny utrzek v kaskadove bubline (v0.9.0).

### Soukromi a vek
- Novy krok onboardingu: datum narozeni (uklada se JEN odvozeny priznak, datum se zahodi) a prehled toho, co z telefonu odchazi.
- Zdroje pro dospele jsou nove VYCHOZE SKRYTE - drive se nabizely rovnou po instalaci.
- Hlaseni padu je nezaskrtnuty souhlas. Do ted se sbiralo v kazdem release buildu natvrdo.
- Obojí jde kdykoli zmenit v Nastaveni -> O aplikaci -> Soukromi.

### Pod kapotou
- **Skok zavislosti**: Kotlin 1.9.24 -> 2.2.21, AGP 8.5.2 -> 8.13.2, Compose BOM 2024.06 -> 2025.12, Room 2.8.4, Gradle 8.13. targetSdk 34 -> 36, overeno na skutecnem Androidu 16.
- Pull-to-refresh prepsan na PullToRefreshBox, TabRow -> SecondaryTabRow a dalsi vynucene migrace.
- **Zadny catch uz chybu nespolkne beze stopy** - 25 mist prevedeno na ErrorReporter.
- Kvota prekladove proxy se uz nestrhava za pokusy, ktere upstream odmitl.
- Uklid jen prohlednute mangy pri startu - tabulka rostla z kazdeho otevreneho detailu a nic ji nemazalo.
- Vsechny instrumentovane testy zapnute (drive 5 z 6 vypnutych) a nezavisle na stavu zarizeni.
- CHANGELOG doplnen o 23 chybejicich verzi (v0.3.4 - v0.7.9).

707 unit testu, 14 instrumentovanych, 0 varovani kompilatoru.

## v0.9.1

Prelozene stranky se pri prvnim otevreni prelozi znovu (PIPELINE_VERSION 11 -> 12) - obrys bubliny se uklada, takze na starych zaznamech by se oprava neprojevila.

### Opravene chyby
- **Cerna placka pres pul panelu tam, kde je vodoznak skenlacni skupiny.** Obrys bubliny se hleda vylitim barvy od bodu kolem OCR textu. U vodoznaku lezicino na tmavem pruhu je plocha kolem nej souvisle tmava pres cely panel, takze se vyliti nezastavilo na zadne hranici. Jedina pojistka byl plosny limit vztazeny ke CELE strance (ctvrtina) - jenze ctvrtina stranky 1440x3120 je pres milion pixelu, do kterych se unikle vyliti pohodlne vejde.

  Zmereno na zarizeni na nahlasene strance (obalovy obdelnik obrysu proti OCR boxu textu):

  | blok | pomer |
  |---|---|
  | "MOUNTAIN BEASTS..." | 2,7x |
  | "GOOD HEAVENS, IT'S A TRAP!" | 4,3x |
  | "DAMN..." (jedno slovo v kulate bubline) | 16,1x |
  | vodoznak "SIRENSCANS.COM" | 54x az 216x |

  Skutecne bubliny tedy konci u 17x, unikle vyliti zacina nad 54x. Novy limit je 30x - lezi mezi nimi s rezervou na obe strany. Pri prekroceni se obrys zahodi a pouzije se heuristicky obdelnik: horsi odhad tvaru, ale nikdy ne placka pres kresbu. Overeno na skutecne strance v sesti ruznych rozlisenich: vodoznak obrys ztratil ve vsech, vsechny tri skutecne bubliny si ho ve vsech nechaly.

## v0.9.0

Prelozene stranky se pri prvnim otevreni prelozi znovu (PIPELINE_VERSION 10 -> 11) - obe opravy nize meni to, co se uklada, takze na starych zaznamech by se neprojevily.

### Opravene chyby
- **Utrzek vety v horni bublince kaskadove ("snehulakove") bubliny zustaval anglicky.** Nahlaseny pripad: horni lalok "...SAY," anglicky, spodni lalok cesky. Dve nezavisle priciny, obe musely padnout:

  **1. Klasifikator ho oznacil za zvukovy efekt** - a SFX se nikdy neposila na preklad ani nevykresluje, takze v bubline zustane original.

  Pravidlo "kratky text velkymi pismeny bez mezer = zvuk" ma jedinou pojistku: seznam beznych kratkych slov, ktera zvuk nejsou. Jenze text se pred porovnanim orezaval jen o `!`, `?`, `.` a mezeru - **carka mezi ne nepatrila**. Do porovnani tedy slo "WAIT," misto "WAIT" a nikdy se netrefilo. Propadla tak i slova, ktera seznam VYSLOVNE chrani: overeno, ze jako zvuk se klasifikovalo "WAIT,", "DAMN,", "NO,", "HEY," i "AH~".

  Nove se oreze veskera okrajova interpunkce (carka, strednik, dvojtecka, vlnovka, uvozovky, CJK protejsky) a navic plati, ze **text pokracujici ve vete zvuk neni** - carka na konci nebo vypustka na zacatku jsou gramaticke znacky pokracovani a funguji v jakemkoli jazyce, na rozdil od rucniho anglickeho seznamu. Do seznamu pribylo i "SAY" a dalsi bezne jednoslovne repliky.

  **2. Prompt si protirecil.** Sekce o vetach pres vic bublin zakazuje nechat bublinu prazdnou, ale sekce CHYBY uvadela "utrzek" jako duvod vratit `[UNTRANSLATED]`. Horni lalok kaskadove bubliny JE utrzek, takze i kdyby se blok poslal, model mel duvod ho odmitnout. Marker je nove vyhrazeny textu, ktery se neda PRECIST (zkomolene OCR, zbytek vodoznaku); kratka nebo nedokoncena veta duvod neni.

## v0.8.9

Hotove preklady zustavaji v platnosti - tahle verze meni jen vykreslovani, ne to, co je ulozene.

### Opravene chyby
- **V nekterych bublinach se pres cesky preklad vznasel rozmazany cizi text.** Zaplata pozadi (ta, co u textu leziciho primo na kresbe zakryva jen tahy pisma a zbytek obrazu nechava byt) se POCITALA z OCR boxu textu, ale VYKRESLOVALA se pres cely box bubliny - a ten je vzdycky vetsi. Kreslila se pres `ContentScale.FillBounds`, takze se maly vyrez roztahl pres velkou plochu. Zbytky tahu, ktere se pri dopoctu nepodarilo docistit, se tim zvetsily, rozmazaly a posunuly mimo sve misto - doprostred prelozeneho textu.

  Zmereno na nahlasene strance: radkovani zbytku 88 px proti 62 px v originale, tedy zvetseni 1,4x. U textu na kresbe muze byt box az 3,3x sirsi nez OCR box, tam bylo roztazeni jeste vetsi.

  Tri opravy:
  - Bublina s **detekovanym obrysem** zaplatu uz nedostava vubec. Flood-fill najde obrys jen tam, kde je uvnitr souvisla plocha jedne barvy - to je definice skutecne nakreslene bubliny, kde je vypln oriznuta tvarem od originalu k nerozeznani. Presne tyhle bubliny byly na nahlasenych snimcich.
  - U zbylych bloku se zaplata pocita presne pres ten obdelnik, pres ktery se vykresli. Obe strany si ho berou ze stejne funkce, takze uz se nemuzou rozejit.
  - Kresli se `FillWidth` + zarovnane k hornimu okraji misto `FillBounds`, takze vyska boxu (ta se ridi delkou textu) uz zaplatu svisle netahne.

- **Pismo se hleda jen v miste, kde ho OCR naslo.** Zaplata ted pokryva vetsi plochu nez drive, a prahovat i ten presah by znamenalo rozmazavat kresbu tam, kde zadny text nikdy nebyl. Mimo textovou oblast se pixely jen opisou.

- **Dve shodne bubliny na strance si mohly prohodit zaplatu.** Dohledavala se pres `blocks.indexOf(block)`; dva bloky se stejnym textem i souradnicemi jsou si podle data class rovny, takze `indexOf` vracel porad ten prvni. Klicem je nove poloha v seznamu.

## v0.8.8

Hotove preklady zustavaji v platnosti - tahle verze meni jen vykreslovani, ne to, co je ulozene.

### Opravene chyby
- **Text sedel v bubline moc vysoko nebo moc nizko.** Obrys bubliny se hleda vylitim barvy a to zabere i OCASEK - ten uzky vybezek, co ukazuje na mluvciho. Text se pritom centroval na obalovy obdelnik celeho obrysu, jenze ten je kvuli ocasku o dost vyssi nez plocha, kde text doopravdy je. Blok se tak vzdycky odtahl smerem k ocasku.

  Zmereno na nahlasene strance: horni lalok mel obrys y=0.488..0.645 (stred 0,567), ale skutecna textova plocha y=0.559..0.645 (stred 0,602) a puvodni anglicky text stred 0,600. Sazba tedy mirila o 3,3 % vysky stranky vys, nez kde text v originale byl - pres sto obrazovych bodu. Nove se centruje na textovou plochu, ktera na originalni umisteni sedi na tri tisiciny.

  Nejvic to bylo videt u kaskadovych bublin, kde ocasek visi na jednom z laloku: horni text se tlacil nahoru, spodni dolu.

## v0.8.7

### Upozorneni pri aktualizaci
- Ulozene preklady se po instalaci prepocitaji — model nove dostava informaci o vetach pres vic bublin, coz meni vysledny preklad.

### Opravene chyby
- **Veta rozdelena do dvou laloku se prekladala po pulkach.** Appka si umi spocitat, ze dve bubliny tvori jednu vetu, a modelu to predava jako fakt — jenze u kaskadove („snehulakove“) bubliny se to nikdy nespustilo. Vyzadovalo se, aby se bubliny vodorovne prekryvaly aspon z 35 %, coz mlcky predpoklada, ze lezi pod sebou. Laloky kaskadove bubliny jsou ale posunute do stran, prave to jim dava ten schodovity tvar.

  Zmereno na nahlasene strance: skutecny prekryv byl **17,8 %**. Model se tedy o souvislosti nedozvedel a kazdou pulku prelozil jako samostatnou vetu. Prah je nove 15 %.

### Jak se to naslo
Dve kreslene rekonstrukce te stranky se od skutecnosti rozesly zrovna v tom, co rozhodovalo. Pipeline proto bezela primo na nahlasenem snimku a namerila, co se doopravdy deje — vcetne dukazu, ze premalovavani bublin, ktere trapilo predchozi verze, je na tehle strance opravdu vyresene.

## v0.8.6

### Upozornění při aktualizaci
- Uložené překlady se po instalaci přepočítají — obrys bublin se počítá jinak a staré záznamy nesou ten původní, přetékající.

### Opravené chyby
- **Kaskádová bublina pořád přemalovávala text.** Oprava z v0.8.5 se u nahlášené stránky vůbec nespustila. Rozhodovala se podle toho, jestli se rámečky rozpoznaného textu vodorovně překrývají aspoň ze čtvrtiny — jenže laloky kaskádové bubliny jsou *záměrně* posunuté do stran (horní vpravo, spodní vlevo), právě to jim dává ten schodovitý tvar, takže se rámečky překrývají sotva.

  Změřeno na emulátoru před opravou: oba bloky dostaly **totožný obrys celého balónu**, tedy přesně stav bez opravy. Nově se místo rámečků ptáme na to podstatné — *pokrývá můj obrys cizí text?* Po opravě mají tytéž bloky každý svůj úsek a ani jeden už na cizí text nesahá.

- **Písmo bylo zhruba o třetinu menší, než mělo.** Velikost se odhadovala z výšky rozpoznaného rámečku pevným dělením, které neodpovídalo skutečnosti. Naměřeno přímo na zařízení: rámeček je u verzálek 0,73× a u textu s malými písmeny 1,05× výška písma. Text tak v bublinách sedí o dost lépe.

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

<!--
Verze v0.3.4 az v0.7.9 se v dobe vydani do CHANGELOGu nezapisovaly. Sekce nize jsou
ZPETNE SESTAVENE z predmetu commitu mezi prislusnymi tagy (2026-08-02) - jsou tedy
strucnejsi a syrovejsi nez rucne psane zaznamy vys, protoze nic jineho k dispozici
neni. Cistky verze (chore/Release/ci/merge) jsou vynechane.
-->

## v0.7.9 (2026-07-31)

- fix: prekladovy prompt varuje pred doslovnymi idiomy a spatnym zvratnym slovesem
- fix: dlaždicovaný/rozházený vodoznak napříč stránkou už není jako text
- fix: tenky vodoznak mezi pulkami repliky rozdelil bublinu na dve

## v0.7.8 (2026-07-31)

- feat: preklad zkusi nejdriv velikost pisma puvodniho originalu, ne rovnou maximum
- fix: model si pod velkou davkou spletl cislovani "id" a preklad skoncil u jine bubliny
- fix: kaskadova bublina s posunutym druhym radkem ztracela pulku textu

## v0.7.7 (2026-07-31)

- fix: preklad se tvaril jako hotovy i kdyz se nic neprelozilo

## v0.7.6 (2026-07-31)

- fix: jedna zasekla stranka zamrazila ukazatel prekladu na 0/N pro celou kapitolu

## v0.7.5 (2026-07-31)

- fix: appka pri prekladu tvrde padala - Coil hardwarova bitmapa + pixel access

## v0.7.4 (2026-07-31)

- fix: detekce tvaru bubliny zerala stovky MB pameti - appka pri prekladu tise umirala

## v0.7.3 (2026-07-31)

- fix: chybejici preklad uz se nevykresli jako anglicky original + oprava sazby do sirsiho obrysu

## v0.7.2 (2026-07-31)

- fix: preklad kapitoly uz nemarni cas na providerovi, ktery odmita obsluhu

## v0.7.1 (2026-07-28)

- feat: vyvazena sazba textu do tvaru bubliny (kosoctvercovy blok jako profesionalni lettering)

## v0.7.0 (2026-07-27)

- fix: skutecna pricina rozbiteho textu v bublinach - lamani slov po pismenech + prepis sazby

## v0.6.2 (2026-07-27)

- fix: 4 dalsi chyby prekladu bublin (skvrna z watermarku, useknuty text, extremni velikosti)

## v0.6.1 (2026-07-27)

- fix: 3 chyby prekladu bublin nahlasene uzivatelem (mizejici bublina, slita placka, useknuta slova)
- chore: odebran CLAUDE.md ze sledovani gitem

## v0.6.0 (2026-07-27)

- docs: anglicka verze README + poznamka o cestine jako hlavnim jazyce prekladu
- fix: citelne jmeno stazenych kapitol pro export na PC + pad stahovani na Androidu 14
- fix: zvednuty svevolny denni limit prekladove proxy (nasazeno)
- fix: rate limit u jednoho providera uz nezastavi cely prekladovy retezec
- feat: 5 vylepseni kvality prekladu (poradi bublin, mene komprese, kontext, ucici se glosar, shape-aware SHOUT)
- fix: 3 problemy prekladu bublin (UNTRANSLATED leak, shape-aware fit, mene agresivni placka)
- revert: vraceny 3 sloupce v mrizce zdroju na Prochazet
- redesign: cistsi karta zdroje na Prochazet (2 sloupce, ikona vedle nazvu)
- feat: karusel oblibenych zdroju na obrazovce Prochazet
- feat: rozsireny info blok na detailu mangy (Origination/Demographic/Published) + sipka u popisu
- fix: overeno a zamitnuto navraceni manhuafast/manhuaus (uzivatelska korekce)
- fix: odstraneno mangafire - Cloudflare Turnstile token, ne jen chybejici auth
- docs: zdokumentovano ctvrte kolo auditu zdroju (fix vs. remove)
- fix: odstraneno 17 zdroju s nereseitelnou ochranou/strukturou (ctvrte kolo)
- fix: tri dalsi zdroje ze tretiho kola auditu (flamecomics, scanvf, wuxiabox)
- fix: NovelFire selektor + Japscan zastarala domena (6f)
- fix: MadaraSource NOVEL zdroje vracely 0 stranek kapitoly (6e)
- fix: EvilManga archivni URL + zdokumentovano 3. kolo Cloudflare testu (6c)
- fix: opraveno 7/10 zdroju s chybejicimi hotlink referery (6d)
- fix: oprava 5 Madara zdroju se zmenenou archivni URL + druhe kolo auditu
- Fix: odstraneno Bato.to - potvrzeno nefunkcni i v realne appce
- Fix: odstraneno ComicK - funguje uz jen jako tracker, ne zdroj obrazku
- Feat: obecny report v Nastaveni, hromadny prepinac adult zdroju + dokonceni auditu zdroju

## v0.5.0 (2026-07-26)

- Feat: menu na kartě zdroje (oblíbené/report) + pripevnena hlavicka Prochazet
- Feat: chunking fix pro novely + prednacitani prekladu dalsi kapitoly
- Feat: gradientova vyplin bublin + entrance animace
- Refactor: OcrEngine bez OkHttpClient, bitmapy přes novy PageBitmapLoader
- Feat: tap-to-flip bublin (originál/preklad) + obrysovy text pro citelnost
- Refactor: rozdeleni ReaderScreen.kt do fokusovanych souboru
- Perf: dávkový překlad kapitoly místo volání API po jedné stránce
- Feat: OpenRouter (Gemma) jako záložní překladač + skloňování jmen v promptu

## v0.4.2 (2026-07-26)

- fix: audit a oprava rozbitych manga/manhwa/manhua zdroju

## v0.4.1 (2026-07-25)

- (jen zvednuti verze)

## v0.4.0 (2026-07-25)

- (jen zvednuti verze)

## v0.3.9 (2026-07-25)

- (jen zvednuti verze)

## v0.3.8 (2026-07-25)

- Fix: CloudflareInterceptor - overit finalni pokus + presnejsi detekce vyzvy
- Feat: pridat zdroj ManhuaUS (manhuaus.com)
- Fix: DemonicScans cerna stranka - rozseknuti obrich obrazku na kousky
- Feat: Groq muze prekladat stejnym "ultra" promptem jako Gemini (komprese/deleni)
- Fix: Gemini rate limit padne na Groq misto oznaceni [UNTRANSLATED]

## v0.3.7 (2026-07-24)

- Feat: box pro preklad kopiruje skutecny tvar bubliny (BubbleClipShape)
- Feat: font podle typu bubliny (tucne na krik, kurziva na myslenku/sepot)
- Feat: layoutTranslationBlocks pouzije presny tvar bubliny misto heuristiky, kdyz je k dispozici
- Feat: propagace tvaru bubliny do TranslatedBlock, cache + migrace starych zaznamu
- Feat: napojit BubbleShapeDetector do OcrEngine.recognize()
- Feat: BubbleShapeDetector - flood-fill detekce tvaru bubliny (cisty JVM algoritmus)
- Docs: fix - pouzit .clip() misto jen .background(color, shape), aby se ostrihl i obsah
- Docs: implementacni plan pro detekci tvaru bubliny a font podle stylu
- Docs: spec pro detekci tvaru bubliny a font podle stylu

## v0.3.6 (2026-07-24)

- (jen zvednuti verze)

## v0.3.5 (2026-07-24)

- (jen zvednuti verze)

## v0.3.4 (2026-07-20)

- Feat: redesign zdrojů (reálná loga, barevné karty) + spolehlivější překlad

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
