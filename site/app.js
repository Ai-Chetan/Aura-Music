// Aura promo site — Apple-style scrollytelling
// Sticky phone + IntersectionObserver swaps screenshot per section.
// Images deploy from site/screenshots/ (copied by pages.yml).
// Local file:// preview falls back to ../docs/screenshots/.

(function () {
  const SCREENS = {
    library:    { file: 'library.png',     label: '01 — Library' },
    download:   { file: 'download.png',    label: '02 — Download' },
    nowplaying: { file: 'now-playing.png', label: '03 — Now Playing' },
    songdetail: { file: 'song-detail.png', label: '04 — Song Detail' },
    backup:     { file: 'backup.png',      label: '05 — Backup' },
    lockscreen: { file: 'lock-screen.png', label: '06 — Lock-screen' },
  };
  const ORDER = Object.keys(SCREENS);

  const phoneImg = document.getElementById('phoneScreen');
  const caption = document.getElementById('phoneCaption');
  const dotsBox = document.getElementById('dots');
  const progressBar = document.getElementById('progressBar');
  const steps = Array.from(document.querySelectorAll('.step'));
  const showcase = document.querySelector('.showcase');

  // Resolve image URL: prefer deployed path, fallback to docs/ for local preview.
  function candidates(key) {
    const f = SCREENS[key].file;
    return ['screenshots/' + f, '../docs/screenshots/' + f];
  }
  function setImageWithFallback(img, key) {
    const [a, b] = candidates(key);
    img.onerror = function () {
      img.onerror = null;
      if (img.getAttribute('src') !== b) img.src = b;
    };
    img.src = a;
  }

  // Preload all (with fallback chain)
  ORDER.forEach((key) => {
    const im = new Image();
    const [a, b] = candidates(key);
    im.onerror = function () { im.onerror = null; im.src = b; };
    im.src = a;
  });
  // Hero mini phone fallback
  document.querySelectorAll('img[data-fallback]').forEach((img) => {
    img.addEventListener('error', function h() {
      img.removeEventListener('error', h);
      img.src = img.getAttribute('data-fallback');
    });
  });

  // Build dots
  const dots = ORDER.map((key, i) => {
    const d = document.createElement('button');
    d.className = 'dot' + (i === 0 ? ' active' : '');
    d.setAttribute('aria-label', SCREENS[key].label);
    d.addEventListener('click', () => {
      steps[i].scrollIntoView({ behavior: 'smooth', block: 'center' });
    });
    dotsBox.appendChild(d);
    return d;
  });

  let current = ORDER[0];
  let swapTimer = null;

  function activate(key) {
    if (key === current) return;
    current = key;
    const idx = ORDER.indexOf(key);

    dots.forEach((d, i) => d.classList.toggle('active', i === idx));
    caption.textContent = SCREENS[key].label;

    // Crossfade swap
    phoneImg.classList.add('fading');
    clearTimeout(swapTimer);
    swapTimer = setTimeout(() => {
      setImageWithFallback(phoneImg, key);
      phoneImg.alt = 'Aura app — ' + SCREENS[key].label;
      phoneImg.onload = () => phoneImg.classList.remove('fading');
      // safety: remove class even if cached (onload may not fire)
      setTimeout(() => phoneImg.classList.remove('fading'), 400);
    }, 180);

    steps.forEach((s) => s.classList.toggle('active', s.dataset.screen === key));
  }

  // Observe steps: the one crossing the middle of the viewport wins.
  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) activate(e.target.dataset.screen);
      });
    },
    { rootMargin: '-42% 0px -42% 0px', threshold: 0 }
  );
  steps.forEach((s) => io.observe(s));

  // Progress bar across whole showcase
  function onScroll() {
    if (!showcase) return;
    const r = showcase.getBoundingClientRect();
    const total = r.height - window.innerHeight;
    const done = Math.min(Math.max(-r.top, 0), Math.max(total, 1));
    const pct = total > 0 ? (done / total) * 100 : 0;
    if (progressBar) progressBar.style.width = pct.toFixed(1) + '%';
  }
  document.addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  // Reveal-on-scroll
  const ro = new IntersectionObserver(
    (entries) => entries.forEach((e) => {
      if (e.isIntersecting) { e.target.classList.add('visible'); ro.unobserve(e.target); }
    }),
    { threshold: 0.12 }
  );
  document.querySelectorAll('.reveal').forEach((el) => ro.observe(el));

  // Init first step
  steps[0].classList.add('active');
  setImageWithFallback(phoneImg, ORDER[0]);

  const y = document.getElementById('year');
  if (y) y.textContent = '· ' + new Date().getFullYear();
})();
