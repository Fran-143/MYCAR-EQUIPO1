-- Canonización NFKC + full casefold Unicode 15.0.0 + blancos; preserva el texto visible.
-- El mapa completo es constante (sin extensiones ni tablas adicionales).
CREATE FUNCTION mycar.normalizar_servicio_otro(p_texto TEXT)
RETURNS TEXT LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE
SET search_path=pg_catalog AS $normalizar$
 WITH mapa AS (SELECT $casefold$ {"A":"a","B":"b","C":"c","D":"d","E":"e","F":"f","G":"g","H":"h","I":"i","J":"j","K":"k","L":"l","M":"m","N":"n","O":"o","P":"p","Q":"q","R":"r","S":"s","T":"t","U":"u","V":"v","W":"w","X":"x","Y":"y","Z":"z","µ":"μ","À":"à","Á":"á","Â":"â","Ã":"ã","Ä":"ä","Å":"å","Æ":"æ","Ç":"ç","È":"è","É":"é","Ê":"ê","Ë":"ë","Ì":"ì","Í":"í","Î":"î","Ï":"ï","Ð":"ð","Ñ":"ñ","Ò":"ò","Ó":"ó","Ô":"ô","Õ":"õ","Ö":"ö","Ø":"ø","Ù":"ù","Ú":"ú","Û":"û","Ü":"ü","Ý":"ý","Þ":"þ","ß":"ss","Ā":"ā","Ă":"ă","Ą":"ą","Ć":"ć","Ĉ":"ĉ","Ċ":"ċ","Č":"č","Ď":"ď","Đ":"đ","Ē":"ē","Ĕ":"ĕ","Ė":"ė","Ę":"ę","Ě":"ě","Ĝ":"ĝ","Ğ":"ğ","Ġ":"ġ","Ģ":"ģ","Ĥ":"ĥ","Ħ":"ħ","Ĩ":"ĩ","Ī":"ī","Ĭ":"ĭ","Į":"į","İ":"i̇","Ĳ":"ĳ","Ĵ":"ĵ","Ķ":"ķ","Ĺ":"ĺ","Ļ":"ļ","Ľ":"ľ","Ŀ":"ŀ","Ł":"ł","Ń":"ń","Ņ":"ņ","Ň":"ň","ŉ":"ʼn","Ŋ":"ŋ","Ō":"ō","Ŏ":"ŏ","Ő":"ő","Œ":"œ","Ŕ":"ŕ","Ŗ":"ŗ","Ř":"ř","Ś":"ś","Ŝ":"ŝ","Ş":"ş","Š":"š","Ţ":"ţ","Ť":"ť","Ŧ":"ŧ","Ũ":"ũ","Ū":"ū","Ŭ":"ŭ","Ů":"ů","Ű":"ű","Ų":"ų","Ŵ":"ŵ","Ŷ":"ŷ","Ÿ":"ÿ","Ź":"ź","Ż":"ż","Ž":"ž","ſ":"s","Ɓ":"ɓ","Ƃ":"ƃ","Ƅ":"ƅ","Ɔ":"ɔ","Ƈ":"ƈ","Ɖ":"ɖ","Ɗ":"ɗ","Ƌ":"ƌ","Ǝ":"ǝ","Ə":"ə","Ɛ":"ɛ","Ƒ":"ƒ","Ɠ":"ɠ","Ɣ":"ɣ","Ɩ":"ɩ","Ɨ":"ɨ","Ƙ":"ƙ","Ɯ":"ɯ","Ɲ":"ɲ","Ɵ":"ɵ","Ơ":"ơ","Ƣ":"ƣ","Ƥ":"ƥ","Ʀ":"ʀ","Ƨ":"ƨ","Ʃ":"ʃ","Ƭ":"ƭ","Ʈ":"ʈ","Ư":"ư","Ʊ":"ʊ","Ʋ":"ʋ","Ƴ":"ƴ","Ƶ":"ƶ","Ʒ":"ʒ","Ƹ":"ƹ","Ƽ":"ƽ","Ǆ":"ǆ","ǅ":"ǆ","Ǉ":"ǉ","ǈ":"ǉ","Ǌ":"ǌ","ǋ":"ǌ","Ǎ":"ǎ","Ǐ":"ǐ","Ǒ":"ǒ","Ǔ":"ǔ","Ǖ":"ǖ","Ǘ":"ǘ","Ǚ":"ǚ","Ǜ":"ǜ","Ǟ":"ǟ","Ǡ":"ǡ","Ǣ":"ǣ","Ǥ":"ǥ","Ǧ":"ǧ","Ǩ":"ǩ","Ǫ":"ǫ","Ǭ":"ǭ","Ǯ":"ǯ","ǰ":"ǰ","Ǳ":"ǳ","ǲ":"ǳ","Ǵ":"ǵ","Ƕ":"ƕ","Ƿ":"ƿ","Ǹ":"ǹ","Ǻ":"ǻ","Ǽ":"ǽ","Ǿ":"ǿ","Ȁ":"ȁ","Ȃ":"ȃ","Ȅ":"ȅ","Ȇ":"ȇ","Ȉ":"ȉ","Ȋ":"ȋ","Ȍ":"ȍ","Ȏ":"ȏ","Ȑ":"ȑ","Ȓ":"ȓ","Ȕ":"ȕ","Ȗ":"ȗ","Ș":"ș","Ț":"ț","Ȝ":"ȝ","Ȟ":"ȟ","Ƞ":"ƞ","Ȣ":"ȣ","Ȥ":"ȥ","Ȧ":"ȧ","Ȩ":"ȩ","Ȫ":"ȫ","Ȭ":"ȭ","Ȯ":"ȯ","Ȱ":"ȱ","Ȳ":"ȳ","Ⱥ":"ⱥ","Ȼ":"ȼ","Ƚ":"ƚ","Ⱦ":"ⱦ","Ɂ":"ɂ","Ƀ":"ƀ","Ʉ":"ʉ","Ʌ":"ʌ","Ɇ":"ɇ","Ɉ":"ɉ","Ɋ":"ɋ","Ɍ":"ɍ","Ɏ":"ɏ","ͅ":"ι","Ͱ":"ͱ","Ͳ":"ͳ","Ͷ":"ͷ","Ϳ":"ϳ","Ά":"ά","Έ":"έ","Ή":"ή","Ί":"ί","Ό":"ό","Ύ":"ύ","Ώ":"ώ","ΐ":"ΐ","Α":"α","Β":"β","Γ":"γ","Δ":"δ","Ε":"ε","Ζ":"ζ","Η":"η","Θ":"θ","Ι":"ι","Κ":"κ","Λ":"λ","Μ":"μ","Ν":"ν","Ξ":"ξ","Ο":"ο","Π":"π","Ρ":"ρ","Σ":"σ","Τ":"τ","Υ":"υ","Φ":"φ","Χ":"χ","Ψ":"ψ","Ω":"ω","Ϊ":"ϊ","Ϋ":"ϋ","ΰ":"ΰ","ς":"σ","Ϗ":"ϗ","ϐ":"β","ϑ":"θ","ϕ":"φ","ϖ":"π","Ϙ":"ϙ","Ϛ":"ϛ","Ϝ":"ϝ","Ϟ":"ϟ","Ϡ":"ϡ","Ϣ":"ϣ","Ϥ":"ϥ","Ϧ":"ϧ","Ϩ":"ϩ","Ϫ":"ϫ","Ϭ":"ϭ","Ϯ":"ϯ","ϰ":"κ","ϱ":"ρ","ϴ":"θ","ϵ":"ε","Ϸ":"ϸ","Ϲ":"ϲ","Ϻ":"ϻ","Ͻ":"ͻ","Ͼ":"ͼ","Ͽ":"ͽ","Ѐ":"ѐ","Ё":"ё","Ђ":"ђ","Ѓ":"ѓ","Є":"є","Ѕ":"ѕ","І":"і","Ї":"ї","Ј":"ј","Љ":"љ","Њ":"њ","Ћ":"ћ","Ќ":"ќ","Ѝ":"ѝ","Ў":"ў","Џ":"џ","А":"а","Б":"б","В":"в","Г":"г","Д":"д","Е":"е","Ж":"ж","З":"з","И":"и","Й":"й","К":"к","Л":"л","М":"м","Н":"н","О":"о","П":"п","Р":"р","С":"с","Т":"т","У":"у","Ф":"ф","Х":"х","Ц":"ц","Ч":"ч","Ш":"ш","Щ":"щ","Ъ":"ъ","Ы":"ы","Ь":"ь","Э":"э","Ю":"ю","Я":"я","Ѡ":"ѡ","Ѣ":"ѣ","Ѥ":"ѥ","Ѧ":"ѧ","Ѩ":"ѩ","Ѫ":"ѫ","Ѭ":"ѭ","Ѯ":"ѯ","Ѱ":"ѱ","Ѳ":"ѳ","Ѵ":"ѵ","Ѷ":"ѷ","Ѹ":"ѹ","Ѻ":"ѻ","Ѽ":"ѽ","Ѿ":"ѿ","Ҁ":"ҁ","Ҋ":"ҋ","Ҍ":"ҍ","Ҏ":"ҏ","Ґ":"ґ","Ғ":"ғ","Ҕ":"ҕ","Җ":"җ","Ҙ":"ҙ","Қ":"қ","Ҝ":"ҝ","Ҟ":"ҟ","Ҡ":"ҡ","Ң":"ң","Ҥ":"ҥ","Ҧ":"ҧ","Ҩ":"ҩ","Ҫ":"ҫ","Ҭ":"ҭ","Ү":"ү","Ұ":"ұ","Ҳ":"ҳ","Ҵ":"ҵ","Ҷ":"ҷ","Ҹ":"ҹ","Һ":"һ","Ҽ":"ҽ","Ҿ":"ҿ","Ӏ":"ӏ","Ӂ":"ӂ","Ӄ":"ӄ","Ӆ":"ӆ","Ӈ":"ӈ","Ӊ":"ӊ","Ӌ":"ӌ","Ӎ":"ӎ","Ӑ":"ӑ","Ӓ":"ӓ","Ӕ":"ӕ","Ӗ":"ӗ","Ә":"ә","Ӛ":"ӛ","Ӝ":"ӝ","Ӟ":"ӟ","Ӡ":"ӡ","Ӣ":"ӣ","Ӥ":"ӥ","Ӧ":"ӧ","Ө":"ө","Ӫ":"ӫ","Ӭ":"ӭ","Ӯ":"ӯ","Ӱ":"ӱ","Ӳ":"ӳ","Ӵ":"ӵ","Ӷ":"ӷ","Ӹ":"ӹ","Ӻ":"ӻ","Ӽ":"ӽ","Ӿ":"ӿ","Ԁ":"ԁ","Ԃ":"ԃ","Ԅ":"ԅ","Ԇ":"ԇ","Ԉ":"ԉ","Ԋ":"ԋ","Ԍ":"ԍ","Ԏ":"ԏ","Ԑ":"ԑ","Ԓ":"ԓ","Ԕ":"ԕ","Ԗ":"ԗ","Ԙ":"ԙ","Ԛ":"ԛ","Ԝ":"ԝ","Ԟ":"ԟ","Ԡ":"ԡ","Ԣ":"ԣ","Ԥ":"ԥ","Ԧ":"ԧ","Ԩ":"ԩ","Ԫ":"ԫ","Ԭ":"ԭ","Ԯ":"ԯ","Ա":"ա","Բ":"բ","Գ":"գ","Դ":"դ","Ե":"ե","Զ":"զ","Է":"է","Ը":"ը","Թ":"թ","Ժ":"ժ","Ի":"ի","Լ":"լ","Խ":"խ","Ծ":"ծ","Կ":"կ","Հ":"հ","Ձ":"ձ","Ղ":"ղ","Ճ":"ճ","Մ":"մ","Յ":"յ","Ն":"ն","Շ":"շ","Ո":"ո","Չ":"չ","Պ":"պ","Ջ":"ջ","Ռ":"ռ","Ս":"ս","Վ":"վ","Տ":"տ","Ր":"ր","Ց":"ց","Ւ":"ւ","Փ":"փ","Ք":"ք","Օ":"օ","Ֆ":"ֆ","և":"եւ","Ⴀ":"ⴀ","Ⴁ":"ⴁ","Ⴂ":"ⴂ","Ⴃ":"ⴃ","Ⴄ":"ⴄ","Ⴅ":"ⴅ","Ⴆ":"ⴆ","Ⴇ":"ⴇ","Ⴈ":"ⴈ","Ⴉ":"ⴉ","Ⴊ":"ⴊ","Ⴋ":"ⴋ","Ⴌ":"ⴌ","Ⴍ":"ⴍ","Ⴎ":"ⴎ","Ⴏ":"ⴏ","Ⴐ":"ⴐ","Ⴑ":"ⴑ","Ⴒ":"ⴒ","Ⴓ":"ⴓ","Ⴔ":"ⴔ","Ⴕ":"ⴕ","Ⴖ":"ⴖ","Ⴗ":"ⴗ","Ⴘ":"ⴘ","Ⴙ":"ⴙ","Ⴚ":"ⴚ","Ⴛ":"ⴛ","Ⴜ":"ⴜ","Ⴝ":"ⴝ","Ⴞ":"ⴞ","Ⴟ":"ⴟ","Ⴠ":"ⴠ","Ⴡ":"ⴡ","Ⴢ":"ⴢ","Ⴣ":"ⴣ","Ⴤ":"ⴤ","Ⴥ":"ⴥ","Ⴧ":"ⴧ","Ⴭ":"ⴭ","ᏸ":"Ᏸ","ᏹ":"Ᏹ","ᏺ":"Ᏺ","ᏻ":"Ᏻ","ᏼ":"Ᏼ","ᏽ":"Ᏽ","ᲀ":"в","ᲁ":"д","ᲂ":"о","ᲃ":"с","ᲄ":"т","ᲅ":"т","ᲆ":"ъ","ᲇ":"ѣ","ᲈ":"ꙋ","Ა":"ა","Ბ":"ბ","Გ":"გ","Დ":"დ","Ე":"ე","Ვ":"ვ","Ზ":"ზ","Თ":"თ","Ი":"ი","Კ":"კ","Ლ":"ლ","Მ":"მ","Ნ":"ნ","Ო":"ო","Პ":"პ","Ჟ":"ჟ","Რ":"რ","Ს":"ს","Ტ":"ტ","Უ":"უ","Ფ":"ფ","Ქ":"ქ","Ღ":"ღ","Ყ":"ყ","Შ":"შ","Ჩ":"ჩ","Ც":"ც","Ძ":"ძ","Წ":"წ","Ჭ":"ჭ","Ხ":"ხ","Ჯ":"ჯ","Ჰ":"ჰ","Ჱ":"ჱ","Ჲ":"ჲ","Ჳ":"ჳ","Ჴ":"ჴ","Ჵ":"ჵ","Ჶ":"ჶ","Ჷ":"ჷ","Ჸ":"ჸ","Ჹ":"ჹ","Ჺ":"ჺ","Ჽ":"ჽ","Ჾ":"ჾ","Ჿ":"ჿ","Ḁ":"ḁ","Ḃ":"ḃ","Ḅ":"ḅ","Ḇ":"ḇ","Ḉ":"ḉ","Ḋ":"ḋ","Ḍ":"ḍ","Ḏ":"ḏ","Ḑ":"ḑ","Ḓ":"ḓ","Ḕ":"ḕ","Ḗ":"ḗ","Ḙ":"ḙ","Ḛ":"ḛ","Ḝ":"ḝ","Ḟ":"ḟ","Ḡ":"ḡ","Ḣ":"ḣ","Ḥ":"ḥ","Ḧ":"ḧ","Ḩ":"ḩ","Ḫ":"ḫ","Ḭ":"ḭ","Ḯ":"ḯ","Ḱ":"ḱ","Ḳ":"ḳ","Ḵ":"ḵ","Ḷ":"ḷ","Ḹ":"ḹ","Ḻ":"ḻ","Ḽ":"ḽ","Ḿ":"ḿ","Ṁ":"ṁ","Ṃ":"ṃ","Ṅ":"ṅ","Ṇ":"ṇ","Ṉ":"ṉ","Ṋ":"ṋ","Ṍ":"ṍ","Ṏ":"ṏ","Ṑ":"ṑ","Ṓ":"ṓ","Ṕ":"ṕ","Ṗ":"ṗ","Ṙ":"ṙ","Ṛ":"ṛ","Ṝ":"ṝ","Ṟ":"ṟ","Ṡ":"ṡ","Ṣ":"ṣ","Ṥ":"ṥ","Ṧ":"ṧ","Ṩ":"ṩ","Ṫ":"ṫ","Ṭ":"ṭ","Ṯ":"ṯ","Ṱ":"ṱ","Ṳ":"ṳ","Ṵ":"ṵ","Ṷ":"ṷ","Ṹ":"ṹ","Ṻ":"ṻ","Ṽ":"ṽ","Ṿ":"ṿ","Ẁ":"ẁ","Ẃ":"ẃ","Ẅ":"ẅ","Ẇ":"ẇ","Ẉ":"ẉ","Ẋ":"ẋ","Ẍ":"ẍ","Ẏ":"ẏ","Ẑ":"ẑ","Ẓ":"ẓ","Ẕ":"ẕ","ẖ":"ẖ","ẗ":"ẗ","ẘ":"ẘ","ẙ":"ẙ","ẚ":"aʾ","ẛ":"ṡ","ẞ":"ss","Ạ":"ạ","Ả":"ả","Ấ":"ấ","Ầ":"ầ","Ẩ":"ẩ","Ẫ":"ẫ","Ậ":"ậ","Ắ":"ắ","Ằ":"ằ","Ẳ":"ẳ","Ẵ":"ẵ","Ặ":"ặ","Ẹ":"ẹ","Ẻ":"ẻ","Ẽ":"ẽ","Ế":"ế","Ề":"ề","Ể":"ể","Ễ":"ễ","Ệ":"ệ","Ỉ":"ỉ","Ị":"ị","Ọ":"ọ","Ỏ":"ỏ","Ố":"ố","Ồ":"ồ","Ổ":"ổ","Ỗ":"ỗ","Ộ":"ộ","Ớ":"ớ","Ờ":"ờ","Ở":"ở","Ỡ":"ỡ","Ợ":"ợ","Ụ":"ụ","Ủ":"ủ","Ứ":"ứ","Ừ":"ừ","Ử":"ử","Ữ":"ữ","Ự":"ự","Ỳ":"ỳ","Ỵ":"ỵ","Ỷ":"ỷ","Ỹ":"ỹ","Ỻ":"ỻ","Ỽ":"ỽ","Ỿ":"ỿ","Ἀ":"ἀ","Ἁ":"ἁ","Ἂ":"ἂ","Ἃ":"ἃ","Ἄ":"ἄ","Ἅ":"ἅ","Ἆ":"ἆ","Ἇ":"ἇ","Ἐ":"ἐ","Ἑ":"ἑ","Ἒ":"ἒ","Ἓ":"ἓ","Ἔ":"ἔ","Ἕ":"ἕ","Ἠ":"ἠ","Ἡ":"ἡ","Ἢ":"ἢ","Ἣ":"ἣ","Ἤ":"ἤ","Ἥ":"ἥ","Ἦ":"ἦ","Ἧ":"ἧ","Ἰ":"ἰ","Ἱ":"ἱ","Ἲ":"ἲ","Ἳ":"ἳ","Ἴ":"ἴ","Ἵ":"ἵ","Ἶ":"ἶ","Ἷ":"ἷ","Ὀ":"ὀ","Ὁ":"ὁ","Ὂ":"ὂ","Ὃ":"ὃ","Ὄ":"ὄ","Ὅ":"ὅ","ὐ":"ὐ","ὒ":"ὒ","ὔ":"ὔ","ὖ":"ὖ","Ὑ":"ὑ","Ὓ":"ὓ","Ὕ":"ὕ","Ὗ":"ὗ","Ὠ":"ὠ","Ὡ":"ὡ","Ὢ":"ὢ","Ὣ":"ὣ","Ὤ":"ὤ","Ὥ":"ὥ","Ὦ":"ὦ","Ὧ":"ὧ","ᾀ":"ἀι","ᾁ":"ἁι","ᾂ":"ἂι","ᾃ":"ἃι","ᾄ":"ἄι","ᾅ":"ἅι","ᾆ":"ἆι","ᾇ":"ἇι","ᾈ":"ἀι","ᾉ":"ἁι","ᾊ":"ἂι","ᾋ":"ἃι","ᾌ":"ἄι","ᾍ":"ἅι","ᾎ":"ἆι","ᾏ":"ἇι","ᾐ":"ἠι","ᾑ":"ἡι","ᾒ":"ἢι","ᾓ":"ἣι","ᾔ":"ἤι","ᾕ":"ἥι","ᾖ":"ἦι","ᾗ":"ἧι","ᾘ":"ἠι","ᾙ":"ἡι","ᾚ":"ἢι","ᾛ":"ἣι","ᾜ":"ἤι","ᾝ":"ἥι","ᾞ":"ἦι","ᾟ":"ἧι","ᾠ":"ὠι","ᾡ":"ὡι","ᾢ":"ὢι","ᾣ":"ὣι","ᾤ":"ὤι","ᾥ":"ὥι","ᾦ":"ὦι","ᾧ":"ὧι","ᾨ":"ὠι","ᾩ":"ὡι","ᾪ":"ὢι","ᾫ":"ὣι","ᾬ":"ὤι","ᾭ":"ὥι","ᾮ":"ὦι","ᾯ":"ὧι","ᾲ":"ὰι","ᾳ":"αι","ᾴ":"άι","ᾶ":"ᾶ","ᾷ":"ᾶι","Ᾰ":"ᾰ","Ᾱ":"ᾱ","Ὰ":"ὰ","Ά":"ά","ᾼ":"αι","ι":"ι","ῂ":"ὴι","ῃ":"ηι","ῄ":"ήι","ῆ":"ῆ","ῇ":"ῆι","Ὲ":"ὲ","Έ":"έ","Ὴ":"ὴ","Ή":"ή","ῌ":"ηι","ῒ":"ῒ","ΐ":"ΐ","ῖ":"ῖ","ῗ":"ῗ","Ῐ":"ῐ","Ῑ":"ῑ","Ὶ":"ὶ","Ί":"ί","ῢ":"ῢ","ΰ":"ΰ","ῤ":"ῤ","ῦ":"ῦ","ῧ":"ῧ","Ῠ":"ῠ","Ῡ":"ῡ","Ὺ":"ὺ","Ύ":"ύ","Ῥ":"ῥ","ῲ":"ὼι","ῳ":"ωι","ῴ":"ώι","ῶ":"ῶ","ῷ":"ῶι","Ὸ":"ὸ","Ό":"ό","Ὼ":"ὼ","Ώ":"ώ","ῼ":"ωι","Ω":"ω","K":"k","Å":"å","Ⅎ":"ⅎ","Ⅰ":"ⅰ","Ⅱ":"ⅱ","Ⅲ":"ⅲ","Ⅳ":"ⅳ","Ⅴ":"ⅴ","Ⅵ":"ⅵ","Ⅶ":"ⅶ","Ⅷ":"ⅷ","Ⅸ":"ⅸ","Ⅹ":"ⅹ","Ⅺ":"ⅺ","Ⅻ":"ⅻ","Ⅼ":"ⅼ","Ⅽ":"ⅽ","Ⅾ":"ⅾ","Ⅿ":"ⅿ","Ↄ":"ↄ","Ⓐ":"ⓐ","Ⓑ":"ⓑ","Ⓒ":"ⓒ","Ⓓ":"ⓓ","Ⓔ":"ⓔ","Ⓕ":"ⓕ","Ⓖ":"ⓖ","Ⓗ":"ⓗ","Ⓘ":"ⓘ","Ⓙ":"ⓙ","Ⓚ":"ⓚ","Ⓛ":"ⓛ","Ⓜ":"ⓜ","Ⓝ":"ⓝ","Ⓞ":"ⓞ","Ⓟ":"ⓟ","Ⓠ":"ⓠ","Ⓡ":"ⓡ","Ⓢ":"ⓢ","Ⓣ":"ⓣ","Ⓤ":"ⓤ","Ⓥ":"ⓥ","Ⓦ":"ⓦ","Ⓧ":"ⓧ","Ⓨ":"ⓨ","Ⓩ":"ⓩ","Ⰰ":"ⰰ","Ⰱ":"ⰱ","Ⰲ":"ⰲ","Ⰳ":"ⰳ","Ⰴ":"ⰴ","Ⰵ":"ⰵ","Ⰶ":"ⰶ","Ⰷ":"ⰷ","Ⰸ":"ⰸ","Ⰹ":"ⰹ","Ⰺ":"ⰺ","Ⰻ":"ⰻ","Ⰼ":"ⰼ","Ⰽ":"ⰽ","Ⰾ":"ⰾ","Ⰿ":"ⰿ","Ⱀ":"ⱀ","Ⱁ":"ⱁ","Ⱂ":"ⱂ","Ⱃ":"ⱃ","Ⱄ":"ⱄ","Ⱅ":"ⱅ","Ⱆ":"ⱆ","Ⱇ":"ⱇ","Ⱈ":"ⱈ","Ⱉ":"ⱉ","Ⱊ":"ⱊ","Ⱋ":"ⱋ","Ⱌ":"ⱌ","Ⱍ":"ⱍ","Ⱎ":"ⱎ","Ⱏ":"ⱏ","Ⱐ":"ⱐ","Ⱑ":"ⱑ","Ⱒ":"ⱒ","Ⱓ":"ⱓ","Ⱔ":"ⱔ","Ⱕ":"ⱕ","Ⱖ":"ⱖ","Ⱗ":"ⱗ","Ⱘ":"ⱘ","Ⱙ":"ⱙ","Ⱚ":"ⱚ","Ⱛ":"ⱛ","Ⱜ":"ⱜ","Ⱝ":"ⱝ","Ⱞ":"ⱞ","Ⱟ":"ⱟ","Ⱡ":"ⱡ","Ɫ":"ɫ","Ᵽ":"ᵽ","Ɽ":"ɽ","Ⱨ":"ⱨ","Ⱪ":"ⱪ","Ⱬ":"ⱬ","Ɑ":"ɑ","Ɱ":"ɱ","Ɐ":"ɐ","Ɒ":"ɒ","Ⱳ":"ⱳ","Ⱶ":"ⱶ","Ȿ":"ȿ","Ɀ":"ɀ","Ⲁ":"ⲁ","Ⲃ":"ⲃ","Ⲅ":"ⲅ","Ⲇ":"ⲇ","Ⲉ":"ⲉ","Ⲋ":"ⲋ","Ⲍ":"ⲍ","Ⲏ":"ⲏ","Ⲑ":"ⲑ","Ⲓ":"ⲓ","Ⲕ":"ⲕ","Ⲗ":"ⲗ","Ⲙ":"ⲙ","Ⲛ":"ⲛ","Ⲝ":"ⲝ","Ⲟ":"ⲟ","Ⲡ":"ⲡ","Ⲣ":"ⲣ","Ⲥ":"ⲥ","Ⲧ":"ⲧ","Ⲩ":"ⲩ","Ⲫ":"ⲫ","Ⲭ":"ⲭ","Ⲯ":"ⲯ","Ⲱ":"ⲱ","Ⲳ":"ⲳ","Ⲵ":"ⲵ","Ⲷ":"ⲷ","Ⲹ":"ⲹ","Ⲻ":"ⲻ","Ⲽ":"ⲽ","Ⲿ":"ⲿ","Ⳁ":"ⳁ","Ⳃ":"ⳃ","Ⳅ":"ⳅ","Ⳇ":"ⳇ","Ⳉ":"ⳉ","Ⳋ":"ⳋ","Ⳍ":"ⳍ","Ⳏ":"ⳏ","Ⳑ":"ⳑ","Ⳓ":"ⳓ","Ⳕ":"ⳕ","Ⳗ":"ⳗ","Ⳙ":"ⳙ","Ⳛ":"ⳛ","Ⳝ":"ⳝ","Ⳟ":"ⳟ","Ⳡ":"ⳡ","Ⳣ":"ⳣ","Ⳬ":"ⳬ","Ⳮ":"ⳮ","Ⳳ":"ⳳ","Ꙁ":"ꙁ","Ꙃ":"ꙃ","Ꙅ":"ꙅ","Ꙇ":"ꙇ","Ꙉ":"ꙉ","Ꙋ":"ꙋ","Ꙍ":"ꙍ","Ꙏ":"ꙏ","Ꙑ":"ꙑ","Ꙓ":"ꙓ","Ꙕ":"ꙕ","Ꙗ":"ꙗ","Ꙙ":"ꙙ","Ꙛ":"ꙛ","Ꙝ":"ꙝ","Ꙟ":"ꙟ","Ꙡ":"ꙡ","Ꙣ":"ꙣ","Ꙥ":"ꙥ","Ꙧ":"ꙧ","Ꙩ":"ꙩ","Ꙫ":"ꙫ","Ꙭ":"ꙭ","Ꚁ":"ꚁ","Ꚃ":"ꚃ","Ꚅ":"ꚅ","Ꚇ":"ꚇ","Ꚉ":"ꚉ","Ꚋ":"ꚋ","Ꚍ":"ꚍ","Ꚏ":"ꚏ","Ꚑ":"ꚑ","Ꚓ":"ꚓ","Ꚕ":"ꚕ","Ꚗ":"ꚗ","Ꚙ":"ꚙ","Ꚛ":"ꚛ","Ꜣ":"ꜣ","Ꜥ":"ꜥ","Ꜧ":"ꜧ","Ꜩ":"ꜩ","Ꜫ":"ꜫ","Ꜭ":"ꜭ","Ꜯ":"ꜯ","Ꜳ":"ꜳ","Ꜵ":"ꜵ","Ꜷ":"ꜷ","Ꜹ":"ꜹ","Ꜻ":"ꜻ","Ꜽ":"ꜽ","Ꜿ":"ꜿ","Ꝁ":"ꝁ","Ꝃ":"ꝃ","Ꝅ":"ꝅ","Ꝇ":"ꝇ","Ꝉ":"ꝉ","Ꝋ":"ꝋ","Ꝍ":"ꝍ","Ꝏ":"ꝏ","Ꝑ":"ꝑ","Ꝓ":"ꝓ","Ꝕ":"ꝕ","Ꝗ":"ꝗ","Ꝙ":"ꝙ","Ꝛ":"ꝛ","Ꝝ":"ꝝ","Ꝟ":"ꝟ","Ꝡ":"ꝡ","Ꝣ":"ꝣ","Ꝥ":"ꝥ","Ꝧ":"ꝧ","Ꝩ":"ꝩ","Ꝫ":"ꝫ","Ꝭ":"ꝭ","Ꝯ":"ꝯ","Ꝺ":"ꝺ","Ꝼ":"ꝼ","Ᵹ":"ᵹ","Ꝿ":"ꝿ","Ꞁ":"ꞁ","Ꞃ":"ꞃ","Ꞅ":"ꞅ","Ꞇ":"ꞇ","Ꞌ":"ꞌ","Ɥ":"ɥ","Ꞑ":"ꞑ","Ꞓ":"ꞓ","Ꞗ":"ꞗ","Ꞙ":"ꞙ","Ꞛ":"ꞛ","Ꞝ":"ꞝ","Ꞟ":"ꞟ","Ꞡ":"ꞡ","Ꞣ":"ꞣ","Ꞥ":"ꞥ","Ꞧ":"ꞧ","Ꞩ":"ꞩ","Ɦ":"ɦ","Ɜ":"ɜ","Ɡ":"ɡ","Ɬ":"ɬ","Ɪ":"ɪ","Ʞ":"ʞ","Ʇ":"ʇ","Ʝ":"ʝ","Ꭓ":"ꭓ","Ꞵ":"ꞵ","Ꞷ":"ꞷ","Ꞹ":"ꞹ","Ꞻ":"ꞻ","Ꞽ":"ꞽ","Ꞿ":"ꞿ","Ꟁ":"ꟁ","Ꟃ":"ꟃ","Ꞔ":"ꞔ","Ʂ":"ʂ","Ᶎ":"ᶎ","Ꟈ":"ꟈ","Ꟊ":"ꟊ","Ꟑ":"ꟑ","Ꟗ":"ꟗ","Ꟙ":"ꟙ","Ꟶ":"ꟶ","ꭰ":"Ꭰ","ꭱ":"Ꭱ","ꭲ":"Ꭲ","ꭳ":"Ꭳ","ꭴ":"Ꭴ","ꭵ":"Ꭵ","ꭶ":"Ꭶ","ꭷ":"Ꭷ","ꭸ":"Ꭸ","ꭹ":"Ꭹ","ꭺ":"Ꭺ","ꭻ":"Ꭻ","ꭼ":"Ꭼ","ꭽ":"Ꭽ","ꭾ":"Ꭾ","ꭿ":"Ꭿ","ꮀ":"Ꮀ","ꮁ":"Ꮁ","ꮂ":"Ꮂ","ꮃ":"Ꮃ","ꮄ":"Ꮄ","ꮅ":"Ꮅ","ꮆ":"Ꮆ","ꮇ":"Ꮇ","ꮈ":"Ꮈ","ꮉ":"Ꮉ","ꮊ":"Ꮊ","ꮋ":"Ꮋ","ꮌ":"Ꮌ","ꮍ":"Ꮍ","ꮎ":"Ꮎ","ꮏ":"Ꮏ","ꮐ":"Ꮐ","ꮑ":"Ꮑ","ꮒ":"Ꮒ","ꮓ":"Ꮓ","ꮔ":"Ꮔ","ꮕ":"Ꮕ","ꮖ":"Ꮖ","ꮗ":"Ꮗ","ꮘ":"Ꮘ","ꮙ":"Ꮙ","ꮚ":"Ꮚ","ꮛ":"Ꮛ","ꮜ":"Ꮜ","ꮝ":"Ꮝ","ꮞ":"Ꮞ","ꮟ":"Ꮟ","ꮠ":"Ꮠ","ꮡ":"Ꮡ","ꮢ":"Ꮢ","ꮣ":"Ꮣ","ꮤ":"Ꮤ","ꮥ":"Ꮥ","ꮦ":"Ꮦ","ꮧ":"Ꮧ","ꮨ":"Ꮨ","ꮩ":"Ꮩ","ꮪ":"Ꮪ","ꮫ":"Ꮫ","ꮬ":"Ꮬ","ꮭ":"Ꮭ","ꮮ":"Ꮮ","ꮯ":"Ꮯ","ꮰ":"Ꮰ","ꮱ":"Ꮱ","ꮲ":"Ꮲ","ꮳ":"Ꮳ","ꮴ":"Ꮴ","ꮵ":"Ꮵ","ꮶ":"Ꮶ","ꮷ":"Ꮷ","ꮸ":"Ꮸ","ꮹ":"Ꮹ","ꮺ":"Ꮺ","ꮻ":"Ꮻ","ꮼ":"Ꮼ","ꮽ":"Ꮽ","ꮾ":"Ꮾ","ꮿ":"Ꮿ","ﬀ":"ff","ﬁ":"fi","ﬂ":"fl","ﬃ":"ffi","ﬄ":"ffl","ﬅ":"st","ﬆ":"st","ﬓ":"մն","ﬔ":"մե","ﬕ":"մի","ﬖ":"վն","ﬗ":"մխ","Ａ":"ａ","Ｂ":"ｂ","Ｃ":"ｃ","Ｄ":"ｄ","Ｅ":"ｅ","Ｆ":"ｆ","Ｇ":"ｇ","Ｈ":"ｈ","Ｉ":"ｉ","Ｊ":"ｊ","Ｋ":"ｋ","Ｌ":"ｌ","Ｍ":"ｍ","Ｎ":"ｎ","Ｏ":"ｏ","Ｐ":"ｐ","Ｑ":"ｑ","Ｒ":"ｒ","Ｓ":"ｓ","Ｔ":"ｔ","Ｕ":"ｕ","Ｖ":"ｖ","Ｗ":"ｗ","Ｘ":"ｘ","Ｙ":"ｙ","Ｚ":"ｚ","𐐀":"𐐨","𐐁":"𐐩","𐐂":"𐐪","𐐃":"𐐫","𐐄":"𐐬","𐐅":"𐐭","𐐆":"𐐮","𐐇":"𐐯","𐐈":"𐐰","𐐉":"𐐱","𐐊":"𐐲","𐐋":"𐐳","𐐌":"𐐴","𐐍":"𐐵","𐐎":"𐐶","𐐏":"𐐷","𐐐":"𐐸","𐐑":"𐐹","𐐒":"𐐺","𐐓":"𐐻","𐐔":"𐐼","𐐕":"𐐽","𐐖":"𐐾","𐐗":"𐐿","𐐘":"𐑀","𐐙":"𐑁","𐐚":"𐑂","𐐛":"𐑃","𐐜":"𐑄","𐐝":"𐑅","𐐞":"𐑆","𐐟":"𐑇","𐐠":"𐑈","𐐡":"𐑉","𐐢":"𐑊","𐐣":"𐑋","𐐤":"𐑌","𐐥":"𐑍","𐐦":"𐑎","𐐧":"𐑏","𐒰":"𐓘","𐒱":"𐓙","𐒲":"𐓚","𐒳":"𐓛","𐒴":"𐓜","𐒵":"𐓝","𐒶":"𐓞","𐒷":"𐓟","𐒸":"𐓠","𐒹":"𐓡","𐒺":"𐓢","𐒻":"𐓣","𐒼":"𐓤","𐒽":"𐓥","𐒾":"𐓦","𐒿":"𐓧","𐓀":"𐓨","𐓁":"𐓩","𐓂":"𐓪","𐓃":"𐓫","𐓄":"𐓬","𐓅":"𐓭","𐓆":"𐓮","𐓇":"𐓯","𐓈":"𐓰","𐓉":"𐓱","𐓊":"𐓲","𐓋":"𐓳","𐓌":"𐓴","𐓍":"𐓵","𐓎":"𐓶","𐓏":"𐓷","𐓐":"𐓸","𐓑":"𐓹","𐓒":"𐓺","𐓓":"𐓻","𐕰":"𐖗","𐕱":"𐖘","𐕲":"𐖙","𐕳":"𐖚","𐕴":"𐖛","𐕵":"𐖜","𐕶":"𐖝","𐕷":"𐖞","𐕸":"𐖟","𐕹":"𐖠","𐕺":"𐖡","𐕼":"𐖣","𐕽":"𐖤","𐕾":"𐖥","𐕿":"𐖦","𐖀":"𐖧","𐖁":"𐖨","𐖂":"𐖩","𐖃":"𐖪","𐖄":"𐖫","𐖅":"𐖬","𐖆":"𐖭","𐖇":"𐖮","𐖈":"𐖯","𐖉":"𐖰","𐖊":"𐖱","𐖌":"𐖳","𐖍":"𐖴","𐖎":"𐖵","𐖏":"𐖶","𐖐":"𐖷","𐖑":"𐖸","𐖒":"𐖹","𐖔":"𐖻","𐖕":"𐖼","𐲀":"𐳀","𐲁":"𐳁","𐲂":"𐳂","𐲃":"𐳃","𐲄":"𐳄","𐲅":"𐳅","𐲆":"𐳆","𐲇":"𐳇","𐲈":"𐳈","𐲉":"𐳉","𐲊":"𐳊","𐲋":"𐳋","𐲌":"𐳌","𐲍":"𐳍","𐲎":"𐳎","𐲏":"𐳏","𐲐":"𐳐","𐲑":"𐳑","𐲒":"𐳒","𐲓":"𐳓","𐲔":"𐳔","𐲕":"𐳕","𐲖":"𐳖","𐲗":"𐳗","𐲘":"𐳘","𐲙":"𐳙","𐲚":"𐳚","𐲛":"𐳛","𐲜":"𐳜","𐲝":"𐳝","𐲞":"𐳞","𐲟":"𐳟","𐲠":"𐳠","𐲡":"𐳡","𐲢":"𐳢","𐲣":"𐳣","𐲤":"𐳤","𐲥":"𐳥","𐲦":"𐳦","𐲧":"𐳧","𐲨":"𐳨","𐲩":"𐳩","𐲪":"𐳪","𐲫":"𐳫","𐲬":"𐳬","𐲭":"𐳭","𐲮":"𐳮","𐲯":"𐳯","𐲰":"𐳰","𐲱":"𐳱","𐲲":"𐳲","𑢠":"𑣀","𑢡":"𑣁","𑢢":"𑣂","𑢣":"𑣃","𑢤":"𑣄","𑢥":"𑣅","𑢦":"𑣆","𑢧":"𑣇","𑢨":"𑣈","𑢩":"𑣉","𑢪":"𑣊","𑢫":"𑣋","𑢬":"𑣌","𑢭":"𑣍","𑢮":"𑣎","𑢯":"𑣏","𑢰":"𑣐","𑢱":"𑣑","𑢲":"𑣒","𑢳":"𑣓","𑢴":"𑣔","𑢵":"𑣕","𑢶":"𑣖","𑢷":"𑣗","𑢸":"𑣘","𑢹":"𑣙","𑢺":"𑣚","𑢻":"𑣛","𑢼":"𑣜","𑢽":"𑣝","𑢾":"𑣞","𑢿":"𑣟","𖹀":"𖹠","𖹁":"𖹡","𖹂":"𖹢","𖹃":"𖹣","𖹄":"𖹤","𖹅":"𖹥","𖹆":"𖹦","𖹇":"𖹧","𖹈":"𖹨","𖹉":"𖹩","𖹊":"𖹪","𖹋":"𖹫","𖹌":"𖹬","𖹍":"𖹭","𖹎":"𖹮","𖹏":"𖹯","𖹐":"𖹰","𖹑":"𖹱","𖹒":"𖹲","𖹓":"𖹳","𖹔":"𖹴","𖹕":"𖹵","𖹖":"𖹶","𖹗":"𖹷","𖹘":"𖹸","𖹙":"𖹹","𖹚":"𖹺","𖹛":"𖹻","𖹜":"𖹼","𖹝":"𖹽","𖹞":"𖹾","𖹟":"𖹿","𞤀":"𞤢","𞤁":"𞤣","𞤂":"𞤤","𞤃":"𞤥","𞤄":"𞤦","𞤅":"𞤧","𞤆":"𞤨","𞤇":"𞤩","𞤈":"𞤪","𞤉":"𞤫","𞤊":"𞤬","𞤋":"𞤭","𞤌":"𞤮","𞤍":"𞤯","𞤎":"𞤰","𞤏":"𞤱","𞤐":"𞤲","𞤑":"𞤳","𞤒":"𞤴","𞤓":"𞤵","𞤔":"𞤶","𞤕":"𞤷","𞤖":"𞤸","𞤗":"𞤹","𞤘":"𞤺","𞤙":"𞤻","𞤚":"𞤼","𞤛":"𞤽","𞤜":"𞤾","𞤝":"𞤿","𞤞":"𞥀","𞤟":"𞥁","𞤠":"𞥂","𞤡":"𞥃"}$casefold$::jsonb AS datos),
 caracteres AS (
   SELECT caracter, posicion FROM regexp_split_to_table(normalize(p_texto,NFKC),'')
     WITH ORDINALITY AS c(caracter,posicion)
 )
 SELECT btrim(regexp_replace(coalesce(string_agg(coalesce(datos->>caracter,caracter),'' ORDER BY posicion),''),'[[:space:]]+',' ','g'))
 FROM caracteres CROSS JOIN mapa;
$normalizar$;

-- Funciones y triggers exclusivos del MVP. La transacción pertenece a Flyway.
-- 06. Funciones deterministas: estados de alertas (los COMMIT son del servicio Spring)
CREATE FUNCTION mycar.estado_por_tiempo(p_objetivo DATE,p_hoy DATE)
RETURNS VARCHAR LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE WHEN p_hoy>=p_objetivo+15 THEN 'VENCIDA'
             WHEN p_hoy>=p_objetivo-7 THEN 'PENDIENTE'
             WHEN p_hoy>=p_objetivo-30 THEN 'PROXIMA'
             ELSE 'PROGRAMADA' END::VARCHAR;
$$;
CREATE FUNCTION mycar.estado_por_km(p_objetivo INTEGER,p_intervalo INTEGER,p_valor INTEGER)
RETURNS VARCHAR LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE WHEN 10::BIGINT*(p_valor::BIGINT-p_objetivo::BIGINT)>=p_intervalo::BIGINT AND p_valor>=p_objetivo THEN 'VENCIDA'
             WHEN p_valor>=p_objetivo THEN 'PENDIENTE'
             WHEN 10::BIGINT*(p_objetivo::BIGINT-p_valor::BIGINT)<=p_intervalo::BIGINT THEN 'PROXIMA'
             ELSE 'PROGRAMADA' END::VARCHAR;
$$;
CREATE FUNCTION mycar.prioridad_estado_alerta(p_estado VARCHAR)
RETURNS INTEGER LANGUAGE SQL IMMUTABLE AS $$
 SELECT CASE p_estado WHEN 'PROGRAMADA' THEN 1 WHEN 'PROXIMA' THEN 2
   WHEN 'PENDIENTE' THEN 3 WHEN 'VENCIDA' THEN 4 ELSE 0 END;
$$;
CREATE FUNCTION mycar.calcular_estado_alerta(
 p_criterio VARCHAR,p_objetivo_fecha DATE,p_hoy DATE,
 p_objetivo_km INTEGER,p_intervalo INTEGER,p_km INTEGER)
RETURNS VARCHAR LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE s_fecha VARCHAR; s_km VARCHAR;
BEGIN
 IF p_criterio='POR_TIEMPO' THEN RETURN mycar.estado_por_tiempo(p_objetivo_fecha,p_hoy); END IF;
 IF p_criterio='POR_KILOMETRAJE' THEN RETURN mycar.estado_por_km(p_objetivo_km,p_intervalo,p_km); END IF;
 IF p_criterio<>'AMBOS' THEN RAISE EXCEPTION 'Criterio desconocido'; END IF;
 s_fecha:=mycar.estado_por_tiempo(p_objetivo_fecha,p_hoy);
 s_km:=mycar.estado_por_km(p_objetivo_km,p_intervalo,p_km);
 IF mycar.prioridad_estado_alerta(s_fecha)>=mycar.prioridad_estado_alerta(s_km) THEN RETURN s_fecha; END IF;
 RETURN s_km;
END $$;

-- 07. Triggers de integridad no expresable mediante CHECK entre tablas.
CREATE FUNCTION mycar.tr_usuario_edad() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_hoy DATE := (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE;
BEGIN
 IF NEW.fecha_nacimiento IS NULL OR
    extract(year FROM age(v_hoy,NEW.fecha_nacimiento)) NOT BETWEEN 18 AND 85 THEN
   RAISE EXCEPTION 'RF01: edad requerida entre 18 y 85 años';
 END IF;
 RETURN NEW;
END $$;
-- Validación de edad se complementa en Spring Boot (edad 18-85 inclusive).
CREATE TRIGGER tg_usuario_edad BEFORE INSERT OR UPDATE OF fecha_nacimiento ON mycar.usuario
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_usuario_edad();

CREATE FUNCTION mycar.tr_validar_vehiculo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_motor RECORD; v_comb VARCHAR; v_aceite VARCHAR; v_hoy INTEGER;
BEGIN
 SELECT codigo,es_electrico INTO v_motor FROM mycar.tipo_motor WHERE id_tipo_motor=NEW.id_tipo_motor;
 IF NOT FOUND THEN RAISE EXCEPTION 'Motor no existe'; END IF;
 v_hoy:=EXTRACT(YEAR FROM (clock_timestamp() AT TIME ZONE 'America/Lima'))::INTEGER;
 IF NEW.anio_fabricacion>v_hoy THEN RAISE EXCEPTION 'El año de fabricación no puede ser futuro'; END IF;
 IF v_motor.codigo='OTRO' AND nullif(btrim(coalesce(NEW.tipo_motor_otro,'')),'') IS NULL THEN
  RAISE EXCEPTION 'Motor OTRO requiere detalle'; END IF;
 IF v_motor.codigo<>'OTRO' AND NEW.tipo_motor_otro IS NOT NULL THEN RAISE EXCEPTION 'Detalle de motor solo aplica a OTRO'; END IF;
 IF v_motor.es_electrico THEN
  IF NEW.id_combustible IS NOT NULL OR NEW.combustible_otro IS NOT NULL OR
     NEW.id_tipo_aceite IS NOT NULL OR NEW.tipo_aceite_otro IS NOT NULL OR NEW.numero_motor IS NOT NULL THEN
    RAISE EXCEPTION 'Motor eléctrico: combustible/aceite/número motor no aplican'; END IF;
 ELSE
  IF NEW.id_combustible IS NULL THEN RAISE EXCEPTION 'Combustible obligatorio salvo motor eléctrico'; END IF;
  SELECT codigo INTO v_comb FROM mycar.combustible WHERE id_combustible=NEW.id_combustible;
  IF v_comb='OTRO' AND nullif(btrim(coalesce(NEW.combustible_otro,'')),'') IS NULL THEN
    RAISE EXCEPTION 'Combustible OTRO requiere detalle'; END IF;
  IF v_comb<>'OTRO' AND NEW.combustible_otro IS NOT NULL THEN RAISE EXCEPTION 'Detalle combustible solo para OTRO'; END IF;
  IF NEW.id_tipo_aceite IS NOT NULL THEN
   SELECT codigo INTO v_aceite FROM mycar.tipo_aceite WHERE id_tipo_aceite=NEW.id_tipo_aceite;
   IF v_aceite='OTRO' AND nullif(btrim(coalesce(NEW.tipo_aceite_otro,'')),'') IS NULL THEN
     RAISE EXCEPTION 'Aceite OTRO requiere detalle'; END IF;
   IF v_aceite<>'OTRO' AND NEW.tipo_aceite_otro IS NOT NULL THEN RAISE EXCEPTION 'Detalle aceite solo para OTRO'; END IF;
  ELSIF NEW.tipo_aceite_otro IS NOT NULL THEN RAISE EXCEPTION 'No hay aceite OTRO seleccionado'; END IF;
 END IF;
 IF TG_OP='UPDATE' THEN
  IF NEW.id_usuario<>OLD.id_usuario THEN RAISE EXCEPTION 'Propietario de vehículo es inmutable'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_vehiculo_validacion BEFORE INSERT OR UPDATE ON mycar.vehiculo
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_vehiculo();

-- La fuente de odómetro es append-only. Las operaciones nuevas se serializan por vehículo.
CREATE FUNCTION mycar.tr_validar_lectura() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_estado VARCHAR;v_ultima INTEGER;v_prop BIGINT;v_op RECORD;
BEGIN
 SELECT estado_vehiculo,id_usuario INTO v_estado,v_prop FROM mycar.vehiculo
  WHERE id_vehiculo=NEW.id_vehiculo FOR UPDATE;
 IF NOT FOUND OR v_estado<>'ACTIVO' THEN RAISE EXCEPTION 'Lectura solo para vehículo activo'; END IF;
 SELECT id_usuario,tipo_operacion INTO v_op FROM mycar.operacion_idempotente WHERE id_operacion=NEW.id_operacion;
 IF NOT FOUND THEN RAISE EXCEPTION 'No hay operación idempotente para la lectura'; END IF;
 IF v_op.id_usuario<>v_prop OR
   (NEW.origen='INICIAL' AND v_op.tipo_operacion<>'REGISTRAR_VEHICULO') OR
   (NEW.origen='ACTUALIZACION' AND v_op.tipo_operacion<>'ACTUALIZAR_KM') THEN
   RAISE EXCEPTION 'Operación idempotente no pertenece a lectura/usuario'; END IF;
 SELECT valor_km INTO v_ultima FROM mycar.lectura_kilometraje WHERE id_vehiculo=NEW.id_vehiculo
   ORDER BY fecha_lectura_utc DESC,id_lectura_km DESC LIMIT 1;
 IF NEW.origen='INICIAL' AND v_ultima IS NOT NULL THEN RAISE EXCEPTION 'La lectura inicial ya existe'; END IF;
 IF NEW.origen='ACTUALIZACION' AND (v_ultima IS NULL OR NEW.valor_km<=v_ultima) THEN
   RAISE EXCEPTION 'RF14 requiere kilometraje estrictamente mayor al vigente'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_lectura_insert BEFORE INSERT ON mycar.lectura_kilometraje
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_lectura();
CREATE FUNCTION mycar.tr_lectura_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Lecturas KM de RF08/RF14 son inmutables; no UPDATE/DELETE'; END $$;
CREATE TRIGGER tg_lectura_no_update BEFORE UPDATE OR DELETE ON mycar.lectura_kilometraje
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_lectura_inmutable();

CREATE FUNCTION mycar.tr_validar_mantenimiento() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_lectura RECORD; v_tipo VARCHAR;
BEGIN
 -- Serializa altas RF17 y altas RF22 sobre el mismo vehículo; service debe respetar mismo orden.
 PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo FOR UPDATE;
 IF TG_OP='UPDATE' THEN
  IF NEW.id_vehiculo<>OLD.id_vehiculo THEN RAISE EXCEPTION 'No cambiar vehículo del mantenimiento'; END IF;
 END IF;
 IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo AND estado_vehiculo='ACTIVO') THEN
  RAISE EXCEPTION 'RF17 registra solo sobre vehículo activo'; END IF;
 IF NEW.fecha_servicio>(clock_timestamp() AT TIME ZONE 'America/Lima')::DATE THEN
  RAISE EXCEPTION 'Fecha de mantenimiento no puede ser futura'; END IF;
 SELECT codigo INTO v_tipo FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento;
 IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento AND activo) THEN
  RAISE EXCEPTION 'Tipo de mantenimiento no activo'; END IF;
 IF v_tipo='OTRO' AND nullif(btrim(coalesce(NEW.servicio_otro,'')),'') IS NULL THEN RAISE EXCEPTION 'Servicio OTRO requiere especificación'; END IF;
 IF v_tipo<>'OTRO' AND NEW.servicio_otro IS NOT NULL THEN RAISE EXCEPTION 'Servicio OTRO solo con tipo OTRO'; END IF;
 IF NEW.origen_km_servicio='LECTURA' THEN
  SELECT valor_km,fecha_lectura_utc INTO v_lectura FROM mycar.lectura_kilometraje
    WHERE id_lectura_km=NEW.id_lectura_km_referencia AND id_vehiculo=NEW.id_vehiculo;
  IF NOT FOUND THEN RAISE EXCEPTION 'Lectura de mantenimiento no existe en este vehículo'; END IF;
  IF v_lectura.valor_km<>NEW.km_servicio THEN
    RAISE EXCEPTION 'KM servicio debe coincidir con lectura del mismo vehículo'; END IF;
  IF NEW.fecha_servicio < (v_lectura.fecha_lectura_utc AT TIME ZONE 'America/Lima')::DATE THEN
    RAISE EXCEPTION 'Fecha de servicio no compatible con lectura seleccionada'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_mantenimiento_validar BEFORE INSERT OR UPDATE ON mycar.mantenimiento
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_mantenimiento();

CREATE FUNCTION mycar.tr_repuesto_propietario() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_prop_m BIGINT;v_prop_r BIGINT;
BEGIN
 SELECT v.id_usuario INTO v_prop_m FROM mycar.mantenimiento m
  JOIN mycar.vehiculo v ON v.id_vehiculo=m.id_vehiculo
  WHERE m.id_mantenimiento=NEW.id_mantenimiento;
 SELECT id_usuario INTO v_prop_r FROM mycar.repuesto WHERE id_repuesto=NEW.id_repuesto;
 IF v_prop_m IS NULL OR v_prop_r IS NULL OR v_prop_m<>v_prop_r THEN
  RAISE EXCEPTION 'Repuesto no pertenece al dueño del mantenimiento'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_mantenimiento_repuesto_prop BEFORE INSERT OR UPDATE ON mycar.mantenimiento_repuesto
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_repuesto_propietario();

CREATE FUNCTION mycar.tr_repuesto_propietario_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.id_usuario<>OLD.id_usuario THEN RAISE EXCEPTION 'No reasignar propietario de repuesto'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_repuesto_propietario_inmutable BEFORE UPDATE OF id_usuario ON mycar.repuesto
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_repuesto_propietario_inmutable();

CREATE FUNCTION mycar.tr_validar_alerta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_tipo VARCHAR;v_ref RECORD;v_ultima BIGINT;v_obj DATE;v_km_actual INTEGER;v_estado_inicial VARCHAR;
BEGIN
 -- Bloquear vehículo antes de validar última referencia: RF17 y RF22 toman el mismo candado.
 PERFORM 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo FOR UPDATE;
 IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.vehiculo WHERE id_vehiculo=NEW.id_vehiculo AND estado_vehiculo='ACTIVO') THEN
   RAISE EXCEPTION 'RF22 solo programa sobre vehículo activo'; END IF;
 SELECT codigo INTO v_tipo FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento;
 IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento AND activo) THEN
  RAISE EXCEPTION 'Tipo de mantenimiento no activo'; END IF;
 IF v_tipo='OTRO' AND nullif(btrim(coalesce(NEW.servicio_otro,'')),'') IS NULL THEN
  RAISE EXCEPTION 'Alerta OTRO requiere servicio específico'; END IF;
 IF v_tipo<>'OTRO' AND NEW.servicio_otro IS NOT NULL THEN RAISE EXCEPTION 'Servicio específico solo para OTRO'; END IF;
 IF TG_OP='INSERT' AND NEW.criterio IN ('POR_TIEMPO','AMBOS') AND NEW.fecha_base IS DISTINCT FROM (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE THEN
  RAISE EXCEPTION 'La fecha base cambió: revisar y confirmar de nuevo'; END IF;
 IF NEW.criterio IN ('POR_TIEMPO','AMBOS') THEN
  IF NEW.unidad_tiempo='SEMANAS' THEN
   v_obj:=NEW.fecha_base + NEW.cantidad_tiempo*7;
  ELSE
   v_obj:=(NEW.fecha_base+make_interval(months=>NEW.cantidad_tiempo))::DATE;
  END IF;
  IF NEW.fecha_objetivo<>v_obj THEN RAISE EXCEPTION 'Fecha objetivo no coincide con base/intervalo'; END IF;
  IF TG_OP='INSERT' AND NEW.fecha_objetivo<=(clock_timestamp() AT TIME ZONE 'America/Lima')::DATE THEN
    RAISE EXCEPTION 'Fecha objetivo debe ser futura al crear alerta'; END IF;
 END IF;
 IF TG_OP='INSERT' AND NEW.id_mantenimiento_referencia IS NOT NULL THEN
   SELECT m.id_mantenimiento,m.estado,m.id_tipo_mantenimiento,m.servicio_otro
    INTO v_ref FROM mycar.mantenimiento m WHERE m.id_mantenimiento=NEW.id_mantenimiento_referencia AND m.id_vehiculo=NEW.id_vehiculo;
   IF NOT FOUND THEN RAISE EXCEPTION 'Referencia mantenimiento no existe en este vehículo'; END IF;
   IF v_ref.estado<>'REALIZADO' OR v_ref.id_tipo_mantenimiento<>NEW.id_tipo_mantenimiento THEN
      RAISE EXCEPTION 'Mantenimiento de referencia no coincide/ no está REALIZADO'; END IF;
   IF TG_OP='INSERT' AND NOT EXISTS (SELECT 1 FROM mycar.tipo_mantenimiento WHERE id_tipo_mantenimiento=NEW.id_tipo_mantenimiento AND activo) THEN
  RAISE EXCEPTION 'Tipo de mantenimiento no activo'; END IF;
 IF v_tipo='OTRO' AND mycar.normalizar_servicio_otro(coalesce(v_ref.servicio_otro,''))<>mycar.normalizar_servicio_otro(coalesce(NEW.servicio_otro,'')) THEN
      RAISE EXCEPTION 'OTRO no coincide con mantenimiento de referencia'; END IF;
   SELECT m.id_mantenimiento INTO v_ultima FROM mycar.mantenimiento m
    WHERE m.id_vehiculo=NEW.id_vehiculo AND m.id_tipo_mantenimiento=NEW.id_tipo_mantenimiento
      AND m.estado='REALIZADO' AND
      (v_tipo<>'OTRO' OR mycar.normalizar_servicio_otro(coalesce(m.servicio_otro,''))=mycar.normalizar_servicio_otro(coalesce(NEW.servicio_otro,'')))
    ORDER BY m.fecha_servicio DESC,m.id_mantenimiento DESC LIMIT 1;
   IF v_ultima IS DISTINCT FROM NEW.id_mantenimiento_referencia THEN
     RAISE EXCEPTION 'El mantenimiento de referencia ya no es el último REALIZADO'; END IF;
 END IF;
 IF TG_OP='INSERT' THEN
  SELECT valor_km INTO v_km_actual FROM mycar.lectura_kilometraje WHERE id_vehiculo=NEW.id_vehiculo
      ORDER BY fecha_lectura_utc DESC,id_lectura_km DESC LIMIT 1;
  IF v_km_actual IS NULL THEN RAISE EXCEPTION 'No hay lectura base del vehículo'; END IF;
  IF NEW.criterio IN ('POR_KILOMETRAJE','AMBOS') AND v_km_actual IS DISTINCT FROM NEW.km_base THEN
    RAISE EXCEPTION 'km_base debe coincidir con última lectura'; END IF;
  v_estado_inicial:=mycar.calcular_estado_alerta(NEW.criterio,NEW.fecha_objetivo,
    (clock_timestamp() AT TIME ZONE 'America/Lima')::DATE,NEW.km_objetivo,NEW.intervalo_km,v_km_actual);
  IF NEW.estado IS DISTINCT FROM v_estado_inicial THEN
     RAISE EXCEPTION 'Estado inicial RF22 inválido: esperado %, recibido %',v_estado_inicial,NEW.estado;
  END IF;
  IF NEW.version_fila<>0 THEN RAISE EXCEPTION 'Alerta inicia en version_fila=0'; END IF;
 END IF;
 IF TG_OP='UPDATE' THEN
  IF ROW(NEW.id_vehiculo,NEW.id_tipo_mantenimiento,NEW.criterio,NEW.origen,NEW.categoria,
         NEW.servicio_otro,NEW.unidad_tiempo,NEW.cantidad_tiempo,NEW.fecha_base,
         NEW.fecha_objetivo,NEW.km_objetivo,NEW.km_base,NEW.intervalo_km,
         NEW.id_mantenimiento_referencia,NEW.firma_equivalencia,NEW.creada_utc)
    IS DISTINCT FROM
     ROW(OLD.id_vehiculo,OLD.id_tipo_mantenimiento,OLD.criterio,OLD.origen,OLD.categoria,
         OLD.servicio_otro,OLD.unidad_tiempo,OLD.cantidad_tiempo,OLD.fecha_base,
         OLD.fecha_objetivo,OLD.km_objetivo,OLD.km_base,OLD.intervalo_km,
         OLD.id_mantenimiento_referencia,OLD.firma_equivalencia,OLD.creada_utc) THEN
    RAISE EXCEPTION 'Atributos de identidad/criterio de alerta no se modifican en RF23'; END IF;
  IF OLD.fecha_vencida_verificable IS NOT NULL AND NEW.fecha_vencida_verificable IS DISTINCT FROM OLD.fecha_vencida_verificable THEN
    RAISE EXCEPTION 'Primera fecha VENCIDA es inmutable'; END IF;
  IF NEW.estado<>OLD.estado AND NEW.version_fila<>OLD.version_fila+1 THEN
    RAISE EXCEPTION 'Cada cambio real incrementa version_fila en uno'; END IF;
  IF NEW.estado=OLD.estado AND NEW.version_fila<>OLD.version_fila THEN
    RAISE EXCEPTION 'Sin cambio no se incrementa version_fila'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_alerta_validar BEFORE INSERT OR UPDATE ON mycar.alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_alerta();

CREATE FUNCTION mycar.tr_validar_evidencia_alerta() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_l RECORD;v_fecha DATE;v_primera BIGINT;
BEGIN
 IF NEW.id_lectura_vencida_evidencia IS NOT NULL THEN
  SELECT id_lectura_km,id_vehiculo,valor_km,fecha_lectura_utc INTO v_l
    FROM mycar.lectura_kilometraje WHERE id_lectura_km=NEW.id_lectura_vencida_evidencia;
  IF NOT FOUND THEN RAISE EXCEPTION 'Lectura evidencia no existe'; END IF;
  IF v_l.id_vehiculo<>NEW.id_vehiculo OR NEW.km_objetivo IS NULL OR
   v_l.fecha_lectura_utc<=NEW.creada_utc OR
   10::BIGINT*(v_l.valor_km::BIGINT-NEW.km_objetivo::BIGINT)<NEW.intervalo_km::BIGINT THEN
    RAISE EXCEPTION 'Lectura no demuestra la VENCIDA de esta alerta'; END IF;
  SELECT l.id_lectura_km INTO v_primera FROM mycar.lectura_kilometraje l
   WHERE l.id_vehiculo=NEW.id_vehiculo AND l.fecha_lectura_utc>NEW.creada_utc
    AND 10::BIGINT*(l.valor_km::BIGINT-NEW.km_objetivo::BIGINT)>=NEW.intervalo_km::BIGINT
   ORDER BY l.fecha_lectura_utc,l.id_lectura_km LIMIT 1;
  IF v_primera IS DISTINCT FROM NEW.id_lectura_vencida_evidencia THEN
    RAISE EXCEPTION 'Debe referenciar primera lectura VENCIDA verificable'; END IF;
  v_fecha:=(v_l.fecha_lectura_utc AT TIME ZONE 'America/Lima')::DATE;
  IF NEW.fecha_vencida_verificable IS DISTINCT FROM v_fecha THEN
    RAISE EXCEPTION 'Fecha VENCIDA KM debe ser fecha local de lectura'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_alerta_evidencia BEFORE INSERT OR UPDATE ON mycar.alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_evidencia_alerta();

CREATE FUNCTION mycar.tr_historial_evidencia_vehiculo() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_alerta BIGINT; v_lectura BIGINT;
BEGIN
 IF NEW.id_lectura_evidencia IS NOT NULL THEN
  SELECT id_vehiculo INTO v_alerta FROM mycar.alerta WHERE id_alerta=NEW.id_alerta;
  SELECT id_vehiculo INTO v_lectura FROM mycar.lectura_kilometraje WHERE id_lectura_km=NEW.id_lectura_evidencia;
  IF v_alerta IS DISTINCT FROM v_lectura THEN RAISE EXCEPTION 'Historial cita lectura de otro vehículo'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_historial_evidencia BEFORE INSERT OR UPDATE ON mycar.historial_alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_historial_evidencia_vehiculo();
CREATE FUNCTION mycar.tr_validar_version_historial() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE v_a RECORD;
BEGIN
 SELECT estado,version_fila INTO v_a FROM mycar.alerta WHERE id_alerta=NEW.id_alerta FOR UPDATE;
 IF NOT FOUND OR v_a.estado IS DISTINCT FROM NEW.estado_nuevo OR
    v_a.version_fila IS DISTINCT FROM NEW.version_destino THEN
    RAISE EXCEPTION 'Historial y estado/version de alerta deben coincidir en la misma transacción';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER tg_historial_version BEFORE INSERT ON mycar.historial_alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_validar_version_historial();
CREATE FUNCTION mycar.tr_historial_inmutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Historial de alertas append-only'; END $$;
CREATE TRIGGER tg_historial_no_mutar BEFORE UPDATE OR DELETE ON mycar.historial_alerta
 FOR EACH ROW EXECUTE FUNCTION mycar.tr_historial_inmutable();


-- FUNCIONES DE LÓGICA DE BD: RF02 (ventana móvil) y RF23 (sincronización transaccional).
-- Ejecutar tras 01_ESQUEMA_MYCAR.sql. Spring Boot sigue siendo responsable de
-- hash de passwords, tokens, autorización de endpoints e idempotencia de POST.

CREATE FUNCTION mycar.registrar_fallo_login(p_id_usuario BIGINT)
RETURNS TIMESTAMPTZ LANGUAGE plpgsql AS $$
DECLARE v_u RECORD; v_now TIMESTAMPTZ; v_count INTEGER;
BEGIN
 SELECT id_usuario,estado_cuenta,login_bloqueo_hasta_utc,login_fallos_desde_utc INTO v_u
   FROM mycar.usuario WHERE id_usuario=p_id_usuario FOR NO KEY UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'No existe usuario para registrar evento de login'; END IF;
 v_now:=clock_timestamp();
 IF v_u.estado_cuenta<>'ACTIVA' THEN RETURN v_u.login_bloqueo_hasta_utc; END IF;
 IF v_u.login_bloqueo_hasta_utc IS NOT NULL AND v_now<v_u.login_bloqueo_hasta_utc THEN
  -- Si está restringido, no verificar passwords ni almacenar más intentos.
  RETURN v_u.login_bloqueo_hasta_utc;
 END IF;
 IF v_u.login_bloqueo_hasta_utc IS NOT NULL AND v_now>=v_u.login_bloqueo_hasta_utc THEN
  UPDATE mycar.usuario SET login_bloqueo_hasta_utc=NULL,login_fallos_desde_utc=v_now WHERE id_usuario=p_id_usuario;
  v_u.login_fallos_desde_utc:=v_now;
 END IF;
 INSERT INTO mycar.intento_login_fallido(id_usuario,ocurrido_utc) VALUES(p_id_usuario,v_now);
 SELECT count(*) INTO v_count FROM mycar.intento_login_fallido
  WHERE id_usuario=p_id_usuario AND ocurrido_utc>=v_now-INTERVAL '10 minutes' AND ocurrido_utc<=v_now
    AND (v_u.login_fallos_desde_utc IS NULL OR ocurrido_utc>=v_u.login_fallos_desde_utc);
 IF v_count>=3 THEN
   UPDATE mycar.usuario SET login_bloqueo_hasta_utc=v_now+INTERVAL '10 minutes' WHERE id_usuario=p_id_usuario;
   RETURN v_now+INTERVAL '10 minutes';
 END IF;
 RETURN NULL;
END $$;

-- IMPORTANTE: antes de verificar contraseña, Spring Boot consulta
-- usuario.login_bloqueo_hasta_utc y cuenta ACTIVA; para el tercer fallo llama
-- esta función y COMMIT. Al vencer un bloqueo, la ventana móvil solo cuenta
-- intentos de los últimos diez minutos. Los login preexistentes no se revocan.
-- Errores públicos genéricos: nunca exponer existencia de correo por la respuesta.

CREATE FUNCTION mycar.sincronizar_alertas_vehiculo(
  p_id_vehiculo BIGINT,p_id_usuario BIGINT)
RETURNS INTEGER LANGUAGE plpgsql AS $$
DECLARE
 v_v mycar.vehiculo%ROWTYPE; v_a mycar.alerta%ROWTYPE;
 v_hoy DATE; v_km INTEGER; v_fecha_tiempo DATE; v_fecha_km DATE;
 v_id_lectura_km BIGINT; v_fecha_evidencia DATE; v_id_evidencia BIGINT;
 v_nuevo VARCHAR; v_anterior VARCHAR; v_cambios INTEGER:=0; v_now TIMESTAMPTZ;
BEGIN
 -- RF23 es un COMANDO de sincronización, NO un GET puro. Bloqueo serial por vehículo.
 SELECT * INTO v_v FROM mycar.vehiculo
  WHERE id_vehiculo=p_id_vehiculo AND id_usuario=p_id_usuario AND estado_vehiculo='ACTIVO'
  FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Vehículo activo no autorizado para sincronización'; END IF;
 IF NOT EXISTS(SELECT 1 FROM mycar.usuario WHERE id_usuario=p_id_usuario AND estado_cuenta='ACTIVA') THEN
  RAISE EXCEPTION 'Cuenta sin autorización'; END IF;
 v_now:=clock_timestamp();
 v_hoy:=(v_now AT TIME ZONE 'America/Lima')::DATE;
 SELECT valor_km INTO v_km FROM mycar.lectura_kilometraje
   WHERE id_vehiculo=p_id_vehiculo ORDER BY fecha_lectura_utc DESC,id_lectura_km DESC LIMIT 1;
 IF v_km IS NULL THEN RAISE EXCEPTION 'No hay lectura efectiva para el vehículo'; END IF;

 FOR v_a IN SELECT * FROM mycar.alerta
   WHERE id_vehiculo=p_id_vehiculo AND estado IN ('PROGRAMADA','PROXIMA','PENDIENTE','VENCIDA')
   ORDER BY id_alerta FOR UPDATE
 LOOP
   v_fecha_tiempo:=NULL; v_fecha_km:=NULL; v_id_lectura_km:=NULL;
   IF v_a.criterio IN ('POR_TIEMPO','AMBOS') AND v_hoy>=v_a.fecha_objetivo+15 THEN
      v_fecha_tiempo:=v_a.fecha_objetivo+15;
   END IF;
   IF v_a.criterio IN ('POR_KILOMETRAJE','AMBOS') THEN
     SELECT l.id_lectura_km,(l.fecha_lectura_utc AT TIME ZONE 'America/Lima')::DATE
      INTO v_id_lectura_km,v_fecha_km
      FROM mycar.lectura_kilometraje l
      WHERE l.id_vehiculo=p_id_vehiculo AND l.fecha_lectura_utc>v_a.creada_utc
       AND 10::BIGINT*(l.valor_km::BIGINT-v_a.km_objetivo::BIGINT)>=v_a.intervalo_km::BIGINT
      ORDER BY l.fecha_lectura_utc ASC,l.id_lectura_km ASC LIMIT 1;
   END IF;
   -- Elegir fuente VERIFICABLE más antigua; empate favorece TIEMPO.
   v_fecha_evidencia:=NULL;v_id_evidencia:=NULL;
   IF v_fecha_tiempo IS NOT NULL AND (v_fecha_km IS NULL OR v_fecha_tiempo<=v_fecha_km) THEN
      v_fecha_evidencia:=v_fecha_tiempo;
   ELSIF v_fecha_km IS NOT NULL THEN
      v_fecha_evidencia:=v_fecha_km;v_id_evidencia:=v_id_lectura_km;
   END IF;
   IF v_a.fecha_vencida_verificable IS NOT NULL THEN
      -- No recalcular ni mover hacia adelante la primera evidencia guardada.
      v_fecha_evidencia:=v_a.fecha_vencida_verificable;
      v_id_evidencia:=v_a.id_lectura_vencida_evidencia;
   END IF;
   v_nuevo:=mycar.calcular_estado_alerta(v_a.criterio,v_a.fecha_objetivo,v_hoy,
                        v_a.km_objetivo,v_a.intervalo_km,v_km);
   -- Si existe evidencia VENCIDA, aún sin sincronizaciones intermedias, adoptar VENCIDA.
   IF v_fecha_evidencia IS NOT NULL AND mycar.prioridad_estado_alerta(v_nuevo)<4 THEN
      v_nuevo:='VENCIDA';
   END IF;
   IF v_fecha_evidencia IS NOT NULL AND v_hoy>=v_fecha_evidencia+30 THEN
      v_nuevo:='ANULADA';
   END IF;
   IF v_nuevo IS DISTINCT FROM v_a.estado THEN
      v_anterior:=v_a.estado;
      -- El UPDATE y la inserción del historial ocurren en ESTA misma transacción.
      UPDATE mycar.alerta SET estado=v_nuevo,version_fila=v_a.version_fila+1,
        fecha_estado_utc=v_now,
        fecha_vencida_verificable=coalesce(v_a.fecha_vencida_verificable,v_fecha_evidencia),
        id_lectura_vencida_evidencia=coalesce(v_a.id_lectura_vencida_evidencia,v_id_evidencia)
      WHERE id_alerta=v_a.id_alerta;
      INSERT INTO mycar.historial_alerta
       (id_alerta,estado_anterior,estado_nuevo,motivo,fecha_cambio_utc,id_lectura_evidencia,version_destino)
      VALUES(v_a.id_alerta,v_anterior,v_nuevo,
        CASE WHEN v_nuevo='ANULADA' THEN 'VENCIMIENTO_30_DIAS' ELSE 'SINCRONIZACION_RF23' END,
        v_now,v_id_evidencia,v_a.version_fila+1);
      v_cambios:=v_cambios+1;
   END IF;
 END LOOP;
 RETURN v_cambios;
END $$;


-- RF02: el servicio llama esta función tras verificar credenciales correctas,
-- dentro de la misma transacción del login. No elimina el historial de fallos.
CREATE FUNCTION mycar.reiniciar_fallos_login(p_id_usuario BIGINT)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE v_u mycar.usuario%ROWTYPE; v_now TIMESTAMPTZ;
BEGIN
 SELECT * INTO v_u FROM mycar.usuario WHERE id_usuario=p_id_usuario FOR NO KEY UPDATE;
 IF NOT FOUND OR v_u.estado_cuenta<>'ACTIVA' THEN RAISE EXCEPTION 'Cuenta sin autorización'; END IF;
 v_now:=clock_timestamp();
 IF v_u.login_bloqueo_hasta_utc IS NOT NULL AND v_now<v_u.login_bloqueo_hasta_utc THEN
  RAISE EXCEPTION 'Cuenta temporalmente restringida'; END IF;
 UPDATE mycar.usuario SET login_bloqueo_hasta_utc=NULL,login_fallos_desde_utc=v_now WHERE id_usuario=p_id_usuario;
END $$;
