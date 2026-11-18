/*
 * TinyChat 共享渲染宿主（M2-5 / M2-6）。
 *
 * 只做三件事：把 LaTeX / Mermaid 源码变成 DOM、把最终尺寸回传给原生层、把主题色注入进来。
 * 真正的「出图」由原生层在隐藏 WebView 上 draw() 完成（列表里不长期挂 WebView）。
 *
 * 与 Kotlin 的契约（见 TcRenderHost.kt）：
 *   调用 window.tcRender(JSON) -- {id, kind:"math"|"mermaid", latex|code, display, fontPx, widthPx, color, theme}
 *   回调 TcBridge.onRender(id, ok:0|1, widthPx, heightPx, error)
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

  function renderMath(req) {
    return ensureKatex().then(function () {
      stage.style.cssText = (req.display ? 'display:block;width:100%;' : 'display:inline-block;') + 'font-size:' + req.fontPx + 'px;color:' + req.color + ';';
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
    var fonts = (document.fonts && document.fonts.ready) ? document.fonts.ready : Promise.resolve();
    fonts.catch(function () {}).then(function () { return frames(1); }).then(function () {
      var box = stage.getBoundingClientRect();
      if (window.TcBridge && TcBridge.onRender) {
        TcBridge.onRender(id, 1, Math.ceil(Math.max(1, box.width)), Math.ceil(Math.max(1, box.height)), '');
      }
    });
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
      // 字体是异步的：不等它量出来的高度会偏小（数学字体比回退字体矮）
      var fonts = (document.fonts && document.fonts.ready) ? document.fonts.ready : Promise.resolve();
      return fonts.catch(function () {}).then(function () { return frames(2); }).then(function () {
        var box = stage.getBoundingClientRect();
        var w = Math.ceil(Math.max(1, box.width));
        var h = Math.ceil(Math.max(1, box.height));
        if (window.TcBridge && TcBridge.onRender) {
          TcBridge.onRender(req.id, res && res.ok ? 1 : 0, w, h, (res && res.error) || '');
        }
      });
    });
  };
})();
