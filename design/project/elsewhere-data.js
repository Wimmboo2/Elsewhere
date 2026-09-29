// Elsewhere prototype data. In production: GeoNames cities15000 (CC BY 4.0), flags as vector drawables.
(function () {
  const pct = (n) => n.toFixed(2) + '%';
  function bands(dir, cols) {
    const list = cols.map((c) => (Array.isArray(c) ? c : [c, 1]));
    const tot = list.reduce((s, c) => s + c[1], 0);
    let acc = 0;
    const st = list.map(([c, w]) => { const a = acc; acc += (w / tot) * 100; return `${c} ${pct(a)} ${pct(acc)}`; });
    return `linear-gradient(${dir},${st.join(',')})`;
  }
  const H = (...c) => bands('to bottom', c);
  const V = (...c) => bands('to right', c);
  // rectangle in % of flag box
  const R = (c, x, y, w, h) => {
    const px = w >= 100 ? 0 : (x / (100 - w)) * 100, py = h >= 100 ? 0 : (y / (100 - h)) * 100;
    return `linear-gradient(${c},${c}) ${pct(px)} ${pct(py)}/${pct(w)} ${pct(h)} no-repeat`;
  };
  const dot = (c, r, x = 50, y = 50) => `radial-gradient(circle closest-side at ${x}% ${y}%,${c} ${r}%,transparent ${r + 3}%)`;
  const nordic = (bg, cross, inner) => [
    ...(inner ? [R(inner, 32, 0, 9, 100), R(inner, 0, 44, 100, 12)] : []),
    R(cross, 28, 0, 17, 100), R(cross, 0, 37, 100, 26), bg,
  ].join(',');
  const F = {
    PT: [dot('#ffcc00', 44, 40), V(['#046a38', 2], ['#da291c', 3])].join(','),
    JP: [dot('#bc002d', 60), '#ffffff'].join(','),
    FR: V('#0055a4', '#ffffff', '#ef4135'),
    IT: V('#009246', '#ffffff', '#ce2b37'),
    DE: H('#1a1a1a', '#dd0000', '#ffce00'),
    ES: H(['#aa151b', 1], ['#f1bf00', 2], ['#aa151b', 1]),
    NL: H('#ae1c28', '#ffffff', '#21468b'),
    IE: V('#169b62', '#ffffff', '#ff883e'),
    GB: [R('#c8102e', 44, 0, 12, 100), R('#c8102e', 0, 40, 100, 20), R('#ffffff', 40, 0, 20, 100), R('#ffffff', 0, 33, 100, 34),
      'linear-gradient(33.7deg,transparent 45%,#c8102e 45% 49%,transparent 49%)', 'linear-gradient(-33.7deg,transparent 51%,#c8102e 51% 55%,transparent 55%)',
      'linear-gradient(33.7deg,transparent 42%,#ffffff 42% 58%,transparent 58%)', 'linear-gradient(-33.7deg,transparent 42%,#ffffff 42% 58%,transparent 58%)', '#012169'].join(','),
    US: [R('#3c3b6e', 0, 0, 40, 54), 'repeating-linear-gradient(to bottom,#b22234 0 7.69%,#ffffff 7.69% 15.38%)'].join(','),
    CA: [dot('#d80621', 42), V(['#d80621', 1], ['#ffffff', 2], ['#d80621', 1])].join(','),
    MX: [dot('#8c6d3f', 26), V('#006847', '#ffffff', '#ce1126')].join(','),
    CO: H(['#fcd116', 2], ['#003893', 1], ['#ce1126', 1]),
    PE: V('#d91023', '#ffffff', '#d91023'),
    AR: [dot('#f6b40e', 26), H('#74acdf', '#ffffff', '#74acdf')].join(','),
    CL: [dot('#ffffff', 38, 16.6, 25), R('#0039a6', 0, 0, 33.3, 50), H('#ffffff', '#d52b1e')].join(','),
    IS: nordic('#02529c', '#ffffff', '#dc1e35'),
    NO: nordic('#ba0c2f', '#ffffff', '#00205b'),
    SE: nordic('#006aa7', '#fecc00'),
    DK: nordic('#c8102e', '#ffffff'),
    FI: nordic('#ffffff', '#002f6c'),
    CH: [R('#ffffff', 44, 20, 12, 60), R('#ffffff', 32, 42, 36, 16), '#da291c'].join(','),
    AT: H('#ed2939', '#ffffff', '#ed2939'),
    BE: V('#1a1a1a', '#fdda24', '#ef3340'),
    PL: H('#ffffff', '#dc143c'),
    GR: [R('#ffffff', 14.8, 0, 7.4, 55.5), R('#ffffff', 0, 22.2, 37, 11.1), R('#0d5eaf', 0, 0, 37, 55.5), 'repeating-linear-gradient(to bottom,#0d5eaf 0 11.11%,#ffffff 11.11% 22.22%)'].join(','),
    EE: H('#0072ce', '#1a1a1a', '#ffffff'),
    HU: H('#ce2939', '#ffffff', '#477050'),
    UA: H('#0057b7', '#ffd700'),
    TR: [dot('#e30a17', 64, 46), dot('#ffffff', 80, 40), '#e30a17'].join(','),
    EG: [dot('#c09300', 30), H('#ce1126', '#ffffff', '#1a1a1a')].join(','),
    MA: [dot('#006233', 40), '#c1272d'].join(','),
    NG: V('#008751', '#ffffff', '#008751'),
    IN: [dot('#000080', 24), H('#ff9933', '#ffffff', '#138808')].join(','),
    TH: H('#a51931', '#f4f5f8', ['#2d2a4a', 2], '#f4f5f8', '#a51931'),
    VN: [dot('#ffcd00', 46), '#da251d'].join(','),
    ID: H('#ff0000', '#ffffff'),
  };
  const raw = [
    ['AR', 'Argentina', 'Buenos Aires,Buenos Aires,-34.6037,-58.3816;Córdoba,Córdoba,-31.4201,-64.1888;Mendoza,Mendoza,-32.8895,-68.8458;Bariloche,Río Negro,-41.1335,-71.3103'],
    ['AT', 'Austria', 'Vienna,Vienna,48.2082,16.3738;Salzburg,Salzburg,47.8095,13.055;Innsbruck,Tyrol,47.2692,11.4041;Graz,Styria,47.0707,15.4395'],
    ['BE', 'Belgium', 'Brussels,Brussels,50.8503,4.3517;Antwerp,Flanders,51.2194,4.4025;Ghent,Flanders,51.0543,3.7174;Bruges,Flanders,51.2093,3.2247;Liège,Wallonia,50.6326,5.5797'],
    ['CA', 'Canada', 'Toronto,Ontario,43.6532,-79.3832;Montréal,Quebec,45.5019,-73.5674;Vancouver,British Columbia,49.2827,-123.1207;Calgary,Alberta,51.0447,-114.0719;Ottawa,Ontario,45.4215,-75.6972;Halifax,Nova Scotia,44.6488,-63.5752'],
    ['CL', 'Chile', 'Santiago,Santiago Metropolitan,-33.4489,-70.6693;Valparaíso,Valparaíso,-33.0472,-71.6127;Punta Arenas,Magallanes,-53.1638,-70.9171'],
    ['CO', 'Colombia', 'Bogotá,Bogotá,4.711,-74.0721;Medellín,Antioquia,6.2442,-75.5812;Cartagena,Bolívar,10.391,-75.4794;Cali,Valle del Cauca,3.4516,-76.532'],
    ['DK', 'Denmark', 'Copenhagen,Capital Region,55.6761,12.5683;Aarhus,Central Jutland,56.1629,10.2039;Odense,Southern Denmark,55.4038,10.4024'],
    ['EG', 'Egypt', 'Cairo,Cairo,30.0444,31.2357;Alexandria,Alexandria,31.2001,29.9187;Luxor,Luxor,25.6872,32.6396'],
    ['EE', 'Estonia', 'Tallinn,Harju,59.437,24.7536;Tartu,Tartu,58.378,26.729'],
    ['FI', 'Finland', 'Helsinki,Uusimaa,60.1699,24.9384;Tampere,Pirkanmaa,61.4978,23.761;Turku,Southwest Finland,60.4518,22.2666;Rovaniemi,Lapland,66.5039,25.7294'],
    ['FR', 'France', 'Paris,Île-de-France,48.8566,2.3522;Lyon,Auvergne-Rhône-Alpes,45.764,4.8357;Marseille,Provence,43.2965,5.3698;Bordeaux,Nouvelle-Aquitaine,44.8378,-0.5792;Nice,Provence,43.7102,7.262;Strasbourg,Grand Est,48.5734,7.7521;Lille,Hauts-de-France,50.6292,3.0573'],
    ['DE', 'Germany', 'Berlin,Berlin,52.52,13.405;Hamburg,Hamburg,53.5511,9.9937;Munich,Bavaria,48.1351,11.582;Cologne,North Rhine-Westphalia,50.9375,6.9603;Leipzig,Saxony,51.3397,12.3731;Frankfurt,Hesse,50.1109,8.6821'],
    ['GR', 'Greece', 'Athens,Attica,37.9838,23.7275;Thessaloniki,Central Macedonia,40.6401,22.9444;Heraklion,Crete,35.3387,25.1442;Fira,South Aegean,36.4167,25.4318'],
    ['HU', 'Hungary', 'Budapest,Budapest,47.4979,19.0402;Debrecen,Hajdú-Bihar,47.5316,21.6273;Pécs,Baranya,46.0727,18.2323'],
    ['IS', 'Iceland', 'Reykjavík,Capital Region,64.1466,-21.9426;Akureyri,Northeastern Region,65.6885,-18.1262;Vík,Southern Region,63.4186,-19.006'],
    ['IN', 'India', 'Mumbai,Maharashtra,19.076,72.8777;Delhi,Delhi,28.7041,77.1025;Bengaluru,Karnataka,12.9716,77.5946;Jaipur,Rajasthan,26.9124,75.7873;Kochi,Kerala,9.9312,76.2673;Kolkata,West Bengal,22.5726,88.3639'],
    ['ID', 'Indonesia', 'Jakarta,Jakarta,-6.2088,106.8456;Denpasar,Bali,-8.6705,115.2126;Yogyakarta,Yogyakarta,-7.7956,110.3695;Bandung,West Java,-6.9175,107.6191'],
    ['IE', 'Ireland', 'Dublin,Leinster,53.3498,-6.2603;Cork,Munster,51.8985,-8.4756;Galway,Connacht,53.2707,-9.0568;Limerick,Munster,52.668,-8.6305;Kilkenny,Leinster,52.6541,-7.2448'],
    ['IT', 'Italy', 'Rome,Lazio,41.9028,12.4964;Milan,Lombardy,45.4642,9.19;Naples,Campania,40.8518,14.2681;Florence,Tuscany,43.7696,11.2558;Venice,Veneto,45.4408,12.3155;Palermo,Sicily,38.1157,13.3615;Bologna,Emilia-Romagna,44.4949,11.3426'],
    ['JP', 'Japan', 'Tokyo,Kantō,35.6762,139.6503;Kyoto,Kansai,35.0116,135.7681;Osaka,Kansai,34.6937,135.5023;Sapporo,Hokkaidō,43.0618,141.3545;Fukuoka,Kyūshū,33.5904,130.4017;Nara,Kansai,34.6851,135.8048;Kanazawa,Chūbu,36.5613,136.6562;Naha,Okinawa,26.2124,127.6809'],
    ['MX', 'Mexico', 'Mexico City,Mexico City,19.4326,-99.1332;Guadalajara,Jalisco,20.6597,-103.3496;Oaxaca,Oaxaca,17.0732,-96.7266;Mérida,Yucatán,20.9674,-89.5926;Monterrey,Nuevo León,25.6866,-100.3161'],
    ['MA', 'Morocco', 'Marrakesh,Marrakesh-Safi,31.6295,-7.9811;Casablanca,Casablanca-Settat,33.5731,-7.5898;Fez,Fès-Meknès,34.0181,-5.0078;Chefchaouen,Tanger-Tetouan,35.1688,-5.2636'],
    ['NL', 'Netherlands', 'Amsterdam,North Holland,52.3676,4.9041;Rotterdam,South Holland,51.9244,4.4777;Utrecht,Utrecht,52.0907,5.1214;The Hague,South Holland,52.0705,4.3007;Groningen,Groningen,53.2194,6.5665;Eindhoven,North Brabant,51.4416,5.4697'],
    ['NG', 'Nigeria', 'Lagos,Lagos,6.5244,3.3792;Abuja,Federal Capital Territory,9.0765,7.3986;Port Harcourt,Rivers,4.8156,7.0498'],
    ['NO', 'Norway', 'Oslo,Oslo,59.9139,10.7522;Bergen,Vestland,60.3913,5.3221;Trondheim,Trøndelag,63.4305,10.3951;Tromsø,Troms,69.6492,18.9553'],
    ['PE', 'Peru', 'Lima,Lima,-12.0464,-77.0428;Cusco,Cusco,-13.5319,-71.9675;Arequipa,Arequipa,-16.409,-71.5375'],
    ['PL', 'Poland', 'Warsaw,Masovia,52.2297,21.0122;Kraków,Lesser Poland,50.0647,19.945;Gdańsk,Pomerania,54.352,18.6466;Wrocław,Lower Silesia,51.1079,17.0385'],
    ['PT', 'Portugal', 'Lisbon,Lisboa,38.7223,-9.1393;Porto,Porto,41.1579,-8.6291;Coimbra,Coimbra,40.2033,-8.4103;Braga,Braga,41.5454,-8.4265;Faro,Algarve,37.0194,-7.9322;Funchal,Madeira,32.6669,-16.9241'],
    ['ES', 'Spain', 'Madrid,Madrid,40.4168,-3.7038;Barcelona,Catalonia,41.3874,2.1686;Seville,Andalusia,37.3891,-5.9845;Valencia,Valencia,39.4699,-0.3763;Bilbao,Basque Country,43.263,-2.935;Palma,Balearic Islands,39.5696,2.6502'],
    ['SE', 'Sweden', 'Stockholm,Stockholm,59.3293,18.0686;Gothenburg,Västra Götaland,57.7089,11.9746;Malmö,Skåne,55.605,13.0038;Uppsala,Uppsala,59.8586,17.6389;Kiruna,Norrbotten,67.8558,20.2253'],
    ['CH', 'Switzerland', 'Zürich,Zürich,47.3769,8.5417;Geneva,Geneva,46.2044,6.1432;Bern,Bern,46.948,7.4474;Lucerne,Lucerne,47.0502,8.3093;Lausanne,Vaud,46.5197,6.6323'],
    ['TH', 'Thailand', 'Bangkok,Bangkok,13.7563,100.5018;Chiang Mai,Chiang Mai,18.7883,98.9853;Phuket,Phuket,7.8804,98.3923'],
    ['TR', 'Türkiye', 'Istanbul,Marmara,41.0082,28.9784;Ankara,Central Anatolia,39.9334,32.8597;Izmir,Aegean,38.4237,27.1428;Antalya,Mediterranean,36.8969,30.7133'],
    ['UA', 'Ukraine', 'Kyiv,Kyiv,50.4501,30.5234;Lviv,Lviv,49.8397,24.0297;Odesa,Odesa,46.4825,30.7233'],
    ['GB', 'United Kingdom', 'London,England,51.5072,-0.1276;Edinburgh,Scotland,55.9533,-3.1883;Manchester,England,53.4808,-2.2426;Bristol,England,51.4545,-2.5879;Cardiff,Wales,51.4816,-3.1791;Belfast,Northern Ireland,54.5973,-5.9301'],
    ['US', 'United States', 'New York,New York,40.7128,-74.006;Los Angeles,California,34.0522,-118.2437;Chicago,Illinois,41.8781,-87.6298;San Francisco,California,37.7749,-122.4194;Austin,Texas,30.2672,-97.7431;Seattle,Washington,47.6062,-122.3321;New Orleans,Louisiana,29.9511,-90.0715;Honolulu,Hawaii,21.3069,-157.8583'],
    ['VN', 'Vietnam', 'Hanoi,Hanoi,21.0278,105.8342;Ho Chi Minh City,Ho Chi Minh City,10.8231,106.6297;Da Nang,Da Nang,16.0544,108.2022;Hội An,Quảng Nam,15.8801,108.338'],
  ];
  const countries = raw.map(([code, name, s]) => ({
    code, name, flag: F[code] || '#999',
    cities: s.split(';').map((c) => { const [n, region, lat, lon] = c.split(','); return { id: code + ':' + n, code, name: n, region, lat: +lat, lon: +lon }; }),
  }));
  const byCode = {}, byCity = {};
  countries.forEach((c) => { byCode[c.code] = c; c.cities.forEach((ci) => { byCity[ci.id] = ci; }); });
  window.ELSEWHERE = { countries, byCode, byCity, flags: F };
})();
