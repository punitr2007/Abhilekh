/* ─── ABHILEKH FRONTEND LOGIC & GENERATIVE ENGINE ──────────────────────────── */

document.addEventListener('DOMContentLoaded', () => {
  initAmbientCanvas();
  initSpotlightEffect();
  initNavbarScroll();
  initComparisonSlider();
  initAadhaarPlayground();
  initBlueprintTabs();
});

/* ─── 1. GENERATIVE AMBIENT CANVAS (PARTICLE FIELD & MESH) ─────────────────── */

function initAmbientCanvas() {
  const canvas = document.getElementById('ambient-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');

  let width = (canvas.width = window.innerWidth);
  let height = (canvas.height = window.innerHeight);

  const particles = [];
  const particleCount = Math.min(65, Math.floor((width * height) / 18000));
  const maxDistance = 140;

  let mouse = { x: width / 2, y: height / 2, active: false };

  window.addEventListener('resize', () => {
    width = canvas.width = window.innerWidth;
    height = canvas.height = window.innerHeight;
  });

  window.addEventListener('mousemove', (e) => {
    mouse.x = e.clientX;
    mouse.y = e.clientY;
    mouse.active = true;
  });

  window.addEventListener('mouseleave', () => {
    mouse.active = false;
  });

  class Particle {
    constructor() {
      this.x = Math.random() * width;
      this.y = Math.random() * height;
      this.vx = (Math.random() - 0.5) * 0.45;
      this.vy = (Math.random() - 0.5) * 0.45;
      this.radius = Math.random() * 1.8 + 0.8;
      this.baseAlpha = Math.random() * 0.4 + 0.15;
    }

    update() {
      this.x += this.vx;
      this.y += this.vy;

      if (this.x < 0) this.x = width;
      if (this.x > width) this.x = 0;
      if (this.y < 0) this.y = height;
      if (this.y > height) this.y = 0;

      // Mouse gentle repulsion
      if (mouse.active) {
        const dx = mouse.x - this.x;
        const dy = mouse.y - this.y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 120) {
          const force = (120 - dist) / 120;
          this.x -= (dx / dist) * force * 1.5;
          this.y -= (dy / dist) * force * 1.5;
        }
      }
    }

    draw() {
      ctx.beginPath();
      ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(96, 165, 250, ${this.baseAlpha})`;
      ctx.fill();
    }
  }

  for (let i = 0; i < particleCount; i++) {
    particles.push(new Particle());
  }

  function animate() {
    ctx.clearRect(0, 0, width, height);

    // Draw connecting filaments
    for (let i = 0; i < particles.length; i++) {
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x;
        const dy = particles[i].y - particles[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < maxDistance) {
          const alpha = (1 - dist / maxDistance) * 0.15;
          ctx.beginPath();
          ctx.moveTo(particles[i].x, particles[i].y);
          ctx.lineTo(particles[j].x, particles[j].y);
          ctx.strokeStyle = `rgba(59, 130, 246, ${alpha})`;
          ctx.lineWidth = 0.8;
          ctx.stroke();
        }
      }
    }

    particles.forEach((p) => {
      p.update();
      p.draw();
    });

    requestAnimationFrame(animate);
  }

  animate();
}

/* ─── 2. SPOTLIGHT MOUSE TRACKING ON GLASS CARDS ───────────────────────────── */

function initSpotlightEffect() {
  const cards = document.querySelectorAll('.spotlight-card');
  cards.forEach((card) => {
    card.addEventListener('mousemove', (e) => {
      const rect = card.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      card.style.setProperty('--mouse-x', `${x}px`);
      card.style.setProperty('--mouse-y', `${y}px`);
    });
  });
}

/* ─── 3. NAVBAR SCROLL EFFECT ──────────────────────────────────────────────── */

function initNavbarScroll() {
  const navbar = document.querySelector('.navbar');
  if (!navbar) return;
  window.addEventListener('scroll', () => {
    if (window.scrollY > 30) {
      navbar.classList.add('scrolled');
    } else {
      navbar.classList.remove('scrolled');
    }
  });
}

/* ─── 4. BEFORE / AFTER COMPARISON SLIDER ──────────────────────────────────── */

function initComparisonSlider() {
  const container = document.querySelector('.comparison-container');
  const cleanImage = document.querySelector('.image-clean-scan');
  const handle = document.querySelector('.comparison-slider-handle');

  if (!container || !cleanImage || !handle) return;

  let isDragging = false;

  function updateSliderPosition(clientX) {
    const rect = container.getBoundingClientRect();
    let x = clientX - rect.left;
    x = Math.max(0, Math.min(x, rect.width));
    const percent = (x / rect.width) * 100;

    cleanImage.style.width = `${percent}%`;
    handle.style.left = `${percent}%`;
  }

  handle.addEventListener('mousedown', () => (isDragging = true));
  window.addEventListener('mouseup', () => (isDragging = false));
  window.addEventListener('mousemove', (e) => {
    if (!isDragging) return;
    updateSliderPosition(e.clientX);
  });

  // Touch Support
  handle.addEventListener('touchstart', () => (isDragging = true));
  window.addEventListener('touchend', () => (isDragging = false));
  window.addEventListener('touchmove', (e) => {
    if (!isDragging || !e.touches[0]) return;
    updateSliderPosition(e.touches[0].clientX);
  });
}

/* ─── 5. LIVE VERHOEFF ALGORITHM AADHAAR PLAYGROUND ────────────────────────── */

// Dihedral Group D5 Multiplication Table
const dTable = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
  [1, 2, 3, 4, 0, 6, 7, 8, 9, 5],
  [2, 3, 4, 0, 1, 7, 8, 9, 5, 6],
  [3, 4, 0, 1, 2, 8, 9, 5, 6, 7],
  [4, 0, 1, 2, 3, 9, 5, 6, 7, 8],
  [5, 9, 8, 7, 6, 0, 4, 3, 2, 1],
  [6, 5, 9, 8, 7, 1, 0, 4, 3, 2],
  [7, 6, 5, 9, 8, 2, 1, 0, 4, 3],
  [8, 7, 6, 5, 9, 3, 2, 1, 0, 4],
  [9, 8, 7, 6, 5, 4, 3, 2, 1, 0]
];

// Permutation Table
const pTable = [
  [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
  [1, 5, 7, 6, 2, 8, 3, 0, 9, 4],
  [5, 8, 0, 3, 7, 9, 6, 1, 4, 2],
  [8, 9, 1, 6, 0, 4, 3, 5, 2, 7],
  [9, 4, 5, 3, 1, 2, 6, 8, 7, 0],
  [4, 2, 8, 6, 5, 7, 3, 9, 0, 1],
  [2, 7, 9, 3, 8, 0, 6, 4, 1, 5],
  [7, 0, 4, 6, 9, 1, 3, 2, 5, 8]
];

function validateVerhoeff(numStr) {
  const clean = numStr.replace(/[^0-9]/g, '');
  if (clean.length !== 12) return false;

  let c = 0;
  const digits = clean.split('').reverse().map(Number);

  for (let i = 0; i < digits.length; i++) {
    c = dTable[c][pTable[i % 8][digits[i]]];
  }
  return c === 0;
}

function initAadhaarPlayground() {
  const input = document.getElementById('aadhaar-test-input');
  const outputDisplay = document.getElementById('aadhaar-redacted-output');
  const statusBadge = document.getElementById('aadhaar-status-badge');
  const checkStatusText = document.getElementById('aadhaar-checksum-text');
  const presetChips = document.querySelectorAll('.preset-chip');

  if (!input || !outputDisplay) return;

  function updateAadhaarDisplay(val) {
    const raw = val.replace(/[^0-9]/g, '');
    let formatted = '';
    for (let i = 0; i < raw.length && i < 12; i++) {
      if (i > 0 && i % 4 === 0) formatted += ' ';
      formatted += raw[i];
    }
    input.value = formatted;

    if (raw.length === 12) {
      const isValidVerhoeff = validateVerhoeff(raw);
      const lastFour = raw.slice(8);

      outputDisplay.innerHTML = `
        <span class="masked-bar">████</span>
        <span class="masked-bar">████</span>
        <span style="color: #60a5fa; font-weight: bold;">${lastFour}</span>
      `;

      if (isValidVerhoeff) {
        statusBadge.innerHTML = `
          <span style="display:inline-flex; align-items:center; gap:4px; color:#10b981; font-weight:bold;">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"/></svg>
            Valid UIDAI Verhoeff Checksum
          </span>
        `;
        checkStatusText.textContent = "Status: Irrevocably masked 8 digits. D5 dihedral checksum validated.";
      } else {
        statusBadge.innerHTML = `
          <span style="display:inline-flex; align-items:center; gap:4px; color:#f59e0b; font-weight:bold;">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
            12 Digits (Checksum Warning)
          </span>
        `;
        checkStatusText.textContent = "Status: 12-digit format matched, but failed Verhoeff checksum test.";
      }
    } else {
      outputDisplay.innerHTML = `<span style="color: rgba(255,255,255,0.3); font-size: 0.95rem;">Enter 12 digits...</span>`;
      statusBadge.innerHTML = `<span style="color: rgba(255,255,255,0.4);">Awaiting Input</span>`;
      checkStatusText.textContent = "Status: Real-time OCR evaluates every bounding box against Verhoeff D5.";
    }
  }

  input.addEventListener('input', (e) => updateAadhaarDisplay(e.target.value));

  presetChips.forEach((chip) => {
    chip.addEventListener('click', () => {
      const val = chip.getAttribute('data-val') || '';
      updateAadhaarDisplay(val);
    });
  });

  // Init with default valid UID
  updateAadhaarDisplay('3214 5678 9012');
}

/* ─── 6. ARCHITECTURE BLUEPRINT CONSOLE TABS ───────────────────────────────── */

const blueprintSnippets = {
  cpp: `<span class="code-comment">// C++17 Morphological Background Estimation & Illumination Division</span>
<span class="code-keyword">void</span> <span class="code-func">processIlluminationDivision</span>(<span class="code-type">uint8_t</span>* pixels, <span class="code-type">int</span> width, <span class="code-type">int</span> height, <span class="code-type">int</span> stride) {
    <span class="code-type">std::vector</span>&lt;<span class="code-type">uint8_t</span>&gt; gray(width * height);
    <span class="code-type">std::vector</span>&lt;<span class="code-type">uint8_t</span>&gt; bgDilated(width * height);

    <span class="code-comment">// 1. Fast Luma Grayscale Conversion</span>
    <span class="code-keyword">for</span> (<span class="code-type">int</span> y = <span class="code-string">0</span>; y &lt; height; ++y) {
        <span class="code-type">uint32_t</span>* row = <span class="code-keyword">reinterpret_cast</span>&lt;<span class="code-type">uint32_t</span>*&gt;(pixels + y * stride);
        <span class="code-keyword">for</span> (<span class="code-type">int</span> x = <span class="code-string">0</span>; x &lt; width; ++x) {
            <span class="code-type">uint32_t</span> c = row[x];
            gray[y * width + x] = (<span class="code-string">299</span> * ((c &gt;&gt; <span class="code-string">16</span>) &amp; <span class="code-string">0xFF</span>) + 
                                   <span class="code-string">587</span> * ((c &gt;&gt; <span class="code-string">8</span>) &amp; <span class="code-string">0xFF</span>) + 
                                   <span class="code-string">114</span> * (c &amp; <span class="code-string">0xFF</span>)) / <span class="code-string">1000</span>;
        }
    }

    <span class="code-comment">// 2. Morphological Box Dilation (Background Estimator)</span>
    <span class="code-type">int</span> kRadius = <span class="code-string">7</span>;
    <span class="code-comment">// ... Subsampled kernel computes local illumination envelope ...</span>

    <span class="code-comment">// 3. Point-wise Illumination Division (Shadow Annihilator)</span>
    <span class="code-keyword">float</span> norm = std::min(<span class="code-string">255.0f</span>, (rawPixel / bgEst) * <span class="code-string">255.0f</span>);
}`,

  pdf: `<span class="code-comment">// Apache PDFBox Searchable Dual-Layer Engine</span>
<span class="code-keyword">object</span> <span class="code-type">PdfBoxEngine</span> {
    <span class="code-keyword">suspend fun</span> <span class="code-func">createSearchablePdf</span>(pages: List&lt;PdfPageInput&gt;, outputFile: File) = withContext(Dispatchers.IO) {
        <span class="code-keyword">val</span> document = PDDocument()
        <span class="code-keyword">for</span> (pageInput <span class="code-keyword">in</span> pages) {
            <span class="code-keyword">val</span> page = PDPage(PDRectangle.A4)
            document.addPage(page)

            PDPageContentStream(document, page).use { stream -&gt;
                <span class="code-comment">// 1. Draw High-Resolution Image Layer</span>
                stream.drawImage(pdImage, <span class="code-string">0f</span>, <span class="code-string">0f</span>, pageWidth, pageHeight)

                <span class="code-comment">// 2. Set PDF Text Rendering Mode 3 ('3 Tr' - Invisible &amp; Selectable)</span>
                stream.setRenderingMode(RenderingMode.NEITHER)
                stream.setFont(PDType1Font.HELVETICA, <span class="code-string">10f</span>)

                <span class="code-comment">// 3. Write OCR Text Glyphs aligned with bounding box coordinates</span>
                stream.showText(cleanAscii(ocrLine.text))
            }
        }
        document.save(outputFile)
    }
}`,

  compose: `<span class="code-comment">// Kotlin Jetpack Compose UDF Studio Architecture</span>
<span class="code-keyword">@Composable</span>
<span class="code-keyword">fun</span> <span class="code-func">DocumentEditorScreen</span>(
    initialPages: List&lt;Bitmap&gt;,
    onSavePdf: (String, List&lt;EditablePage&gt;) -&gt; Unit
) {
    <span class="code-keyword">val</span> pagerState = rememberPagerState { pages.size }
    
    Scaffold(
        topBar = { EditorTopBar(onSave = { onSavePdf(title, pages) }) },
        bottomBar = {
            EditorActionBar(
                onCrop = { launchCropAdjuster() },
                onFilter = { showFilterBottomSheet() },
                onRotate = { rotate90Clockwise() },
                onRedact = { openManualRedactor() }
            )
        }
    ) { padding -&gt;
        HorizontalPager(state = pagerState) { idx -&gt;
            HighResScanViewer(page = pages[idx])
        }
    }
}`
};

function initBlueprintTabs() {
  const tabs = document.querySelectorAll('.blueprint-tab');
  const codeView = document.getElementById('blueprint-code');
  if (!tabs.length || !codeView) return;

  tabs.forEach((tab) => {
    tab.addEventListener('click', () => {
      tabs.forEach((t) => t.classList.remove('active'));
      tab.classList.add('active');
      const key = tab.getAttribute('data-lang');
      if (key && blueprintSnippets[key]) {
        codeView.innerHTML = blueprintSnippets[key];
      }
    });
  });
}
