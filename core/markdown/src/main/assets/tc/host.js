/*
 * TinyChat 共享渲染宿主（M2-5 / M2-6）。
 *
 * 三件事：把 LaTeX / Mermaid 源码变成 DOM、把**尺寸与基线**回传给原生层、注入主题色。
 * 真正的「出图」由原生层在隐藏 WebView 上 draw() 完成（列表里不长期挂 WebView）。
 *
 * 与 Kotlin 的契约（见 TcRenderHost.kt）：
 *   调用 window.tcRender(JSON) -- {id, kind:"math"|"mermaid", latex|code, display, fontPx, widthPx, color, theme}
 *   回调 TcBridge.onRender(id, ok:0|1, x, y, widthPx, heightPx, baselinePx, error)   ← 全是 CSS px
 *
 * (x, y) 是 `#stage` 盒子的左上角（视口坐标），原生层按这块矩形裁剪位图；
 * baselinePx 是**公式基线**相对盒子上边的距离，行内公式靠它与正文共用一条基线。
 */
(function () {
  var stage = document.getElementById('stage');
  var katexReady = false;
  var mermaidReady = false;

  function loadScript(src) {
    return new Promise(function (resolve, reject) {
      var s = document.createElement('script');
      s.src = src;
      s.onload = function () { resolve(); };
      s.onerror = function () { reject(new Error('加载失败: ' + src)); };
      document.head.appendChild(s);
    });
  }

  function ensureKatex() {
    if (katexReady) return Promise.resolve();
    return loadScript('../katex/katex.min.js').then(function () {
      katexReady = true;
      // KaTeX 的数学字体按需加载：不显式 load，首图会拿回退字体画（根号空白、上标错位）
      var families = ['KaTeX_Main', 'KaTeX_Math', 'KaTeX_Size1', 'KaTeX_Size2', 'KaTeX_Size3',
        'KaTeX_Size4', 'KaTeX_AMS', 'KaTeX_Caligraphic', 'KaTeX_Script', 'KaTeX_Fraktur',
        'KaTeX_SansSerif', 'KaTeX_Typewriter'];
      return Promise.all(families.map(function (family) {
        return document.fonts.load('16px ' + family).catch(function () {});
      }));
    });
  }

  function ensureMermaid() {
    if (mermaidReady) return Promise.resolve();
    return loadScript('../mermaid/mermaid.min.js').then(function () { mermaidReady = true; });
  }

  function frames(n) {
    return new Promise(function (resolve) {
      function step() {
        if (--n <= 0) return resolve();
        requestAnimationFrame(step);
      }
      requestAnimationFrame(step);
    });
  }

  /**
   * 基线怎么拿：在同一行盒里临时插一个零宽零高的 inline-block 探针，
   * 它的底边就落在该行盒的基线上。行内公式要与正文对齐的正是这条线 ——
   * 靠猜上下边距永远对不准，因为分式、根号、求和号各自会把盒子顶高。
   * 拿不到就回 0，原生层退回「底边贴基线」。
   */
  function baselineOf() {
    var probe = document.createElement('span');
    probe.setAttribute('style',
      'display:inline-block;width:0;height:0;margin:0;padding:0;border:0;vertical-align:baseline;');
    stage.appendChild(probe);
    try {
      return probe.getBoundingClientRect().bottom;
    } catch (e) {
      return 0;
    } finally {
      stage.removeChild(probe);
    }
  }

  function measureBaseline() {
    var b = baselineOf();
    if (!(b > 0 && b < 100000)) {
      // 探针失效时退到盒子底边
      b = stage.getBoundingClientRect().bottom;
    }
    return Math.round(b);
  }

  /**
   * 摘掉 KaTeX 塞进 DOM 的零宽空格（U+200B）。
   *
   * `.vlist-s` 那个占位格里的 ZWSP 本来应该是「不可见、零宽」的，但 WebView 的系统字体
   * 回退把它当成缺字，直接画成一颗**巨大的 💣 彩色 emoji**（实测约 70 CSS px），
   * 挂在每个含上下标/分式的公式下方，还会把位图撑宽。它纯粹是排版脚手架，
   * 删掉文本不影响任何尺寸（格子宽高由 CSS 定死）。
   */
  function stripZeroWidth() {
    if (!document.createTreeWalker) return;
    var walker = document.createTreeWalker(stage, NodeFilter.SHOW_TEXT, null);
    var node;
    while ((node = walker.nextNode())) {
      if (node.nodeValue && node.nodeValue.indexOf('\u200B') >= 0) {
        node.nodeValue = node.nodeValue.replace(/\u200B/g, '');
      }
    }
  }

  /**
   * 清掉 body 里除 `#stage` 与脚本之外的残留节点。
   *
   * mermaid 的 `render()` 会往 body 里塞一个临时容器（`<div id="d...">`），**报错时不会自己清理**，
   * 而它的报错图（一颗 💣 + “Syntax error in text”）就画在 stage 下方，变成一块巨大的幽灵墨迹，
   * 再被位图裁进来 —— 现象是「每个公式下面都露出一小撮红色图案」。
   */
  function purgeStrayNodes() {
    var kids = document.body.children;
    for (var i = kids.length - 1; i >= 0; i--) {
      var el = kids[i];
      if (el === stage) continue;
      if (el.tagName === 'SCRIPT' || el.tagName === 'STYLE' || el.tagName === 'LINK') continue;
      el.parentNode.removeChild(el);
    }
  }

  function report(id, ok, error, settleFrames) {
    // 字体是异步的：不等它量出来的高度会偏小（数学字体比回退字体矮）
    var fonts = (document.fonts && document.fonts.ready) ? document.fonts.ready : Promise.resolve();
    var wait = settleFrames || 2;
    return fonts.catch(function () {}).then(function () { return frames(wait); }).then(function () {
      purgeStrayNodes();
      stripZeroWidth();
      if (window.TcBridge && TcBridge.onRender) {
        TcBridge.onRender(id, ok ? 1 : 0, measureBaseline(), error || '');
      }
    });
  }

  function renderMath(req) {
    return ensureKatex().then(function () {
      stage.style.cssText = (req.display ? 'display:block;width:100%;' : 'display:inline-block;')
        + 'font-size:' + req.fontPx + 'px;color:' + req.color + ';';
      try {
        katex.render(String(req.latex), stage, {
          displayMode: !!req.display,
          throwOnError: false,
          errorColor: '#c0392b',
          strict: 'ignore',
          trust: false,
          output: 'html'
        });
      } catch (e) {
        stage.textContent = String(e && e.message || e);
        stage.className = 'tcerr';
        return { ok: false, error: String(e && e.message || e) };
      }
      return { ok: true };
    });
  }

  function renderMermaid(req) {
    return ensureMermaid().then(function () {
      var width = Math.max(320, req.widthPx || 800);
      stage.style.cssText = 'display:block;width:' + width + 'px;';
      window.mermaid.initialize({
        startOnLoad: false,
        securityLevel: 'strict',
        theme: req.theme === 'dark' ? 'dark' : 'default',
        fontFamily: 'sans-serif',
        flowchart: { htmlLabels: false, useMaxWidth: false },
        sequence: { useMaxWidth: false },
        class: { useMaxWidth: false },
        state: { useMaxWidth: false },
        pie: { useMaxWidth: false },
        themeVariables: { fontFamily: 'sans-serif' }
      });
      var id = 'tcg' + req.id;
      return window.mermaid.render(id, String(req.code)).then(function (out) {
        stage.innerHTML = out.svg;
        var svg = stage.querySelector('svg');
        if (svg) {
          svg.removeAttribute('height');
          svg.style.maxWidth = '100%';
          svg.style.height = 'auto';
          svg.style.display = 'block';
        }
        return { ok: true };
      }).catch(function (e) {
        stage.innerHTML = '';
        return { ok: false, error: String(e && e.message || e) };
      });
    });
  }

  /**
   * 原生层改完视口尺寸后调用：只量尺寸、不重渲染。
   * 为什么要二次量：先量再改尺寸会让页面重排一次，公式换行后比第一次更高，直接被裁掉半行。
   */
  window.tcMeasure = function (id) {
    // 只需要 1 帧：此时字体早已加载完，等帧只是为了拿到重排后的布局
    report(id, true, '', 1);
  };

  window.tcRender = function (json) {
    var req;
    try {
      req = JSON.parse(json);
    } catch (e) {
      return;
    }
    var started = req.kind === 'mermaid' ? renderMermaid(req) : renderMath(req);
    started.catch(function (e) {
      return { ok: false, error: String(e && e.message || e) };
    }).then(function (res) {
      return report(req.id, res && res.ok, (res && res.error) || '');
    });
  };
})();
