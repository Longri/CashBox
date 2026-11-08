/*
 * Bildausschnitt wählen – Verhalten des Elements <cashbox-image-cropper> (Java: GoalImageCropper).
 *
 * Aufbau (vom Server erzeugt):
 *   <cashbox-image-cropper crop-x crop-y crop-size min-crop zoom-out max-size>
 *     <div class="image-cropper__stage">            quadratische Bühne, hier wird gezogen
 *       <div class="image-cropper__backdrop">       Hintergrund im Kreis, sichtbar beim Herauszoomen
 *       <img class="image-cropper__image">          das Quellbild in Originalgröße, per CSS-Transform skaliert
 *       <div class="image-cropper__frame">          Kreis = Ausschnitt; außen abgedunkelt
 *     </div>
 *     <div class="image-cropper__controls">
 *       <button class="image-cropper__zoom-out"> <input type="range" class="image-cropper__zoom"> <button …zoom-in>
 *     </div>
 *
 * Bedienung: Ziehen verschiebt (Maus, Finger, Stift), zwei Finger oder Mausrad bzw. Trackpad zoomen um
 * die Fingermitte bzw. den Mauszeiger, außerdem Regler und Knöpfe. Auf der fokussierten Bühne
 * verschieben die Pfeiltasten, + und − zoomen.
 *
 * Der Ausschnitt ist relativ zum Bild: Mittelpunkt x/y (0–1) und Kantenlänge "size" als Anteil der
 * kürzeren Bildseite. 1 = das Bild füllt den Ausschnitt gerade, kleiner = hineingezoomt, größer =
 * herausgezoomt (das Bild wird kleiner als der Kreis, der Rand bleibt später durchsichtig). Pro Achse
 * gilt dieselbe Regel wie beim Zuschneiden auf dem Server (ImageResizer.cropSquare): ein kleinerer
 * Ausschnitt bleibt im Bild, ein größerer umschließt es, der Mittelpunkt bleibt im Bild.
 * Bei 100 % rastet der Zoom ein (beim Überqueren bzw. nahe der Reglerstelle), damit man ohne schmale
 * leere Ränder wieder „füllend“ landet.
 *
 * Nach jeder Geste wird das Ereignis "crop-change" (detail: { x, y, size }) ausgelöst; der Server
 * merkt sich die Werte.
 */
(function () {
  'use strict';
  if (customElements.get('cashbox-image-cropper')) {
    return;
  }

  const MAX_ZOOM = 8;       // höchstens 8-fach hineinzoomen
  const SLIDER_SNAP = 0.02; // Reglerbereich um 100 %, in dem der Zoom einrastet
  const ZOOM_STEP = 1.25;  // Faktor je Knopfdruck bzw. Taste
  const KEY_PAN = 0.05;    // Pfeiltaste: Anteil der Ausschnittbreite (mit Umschalt das Fünffache)
  const NOTIFY_DELAY = 200; // ms Ruhe nach Mausrad bzw. Taste, bevor der Server Bescheid bekommt

  /** Alle angezeigten Editoren – um vor einem Klick (z. B. auf „Übernehmen“) offene Meldungen zu senden. */
  const active = new Set();
  document.addEventListener('pointerdown', () => active.forEach((cropper) => cropper.flush()), true);

  /**
   * Mittelpunkt einer Achse begrenzen; half = halbe Ausschnittlänge relativ zur Achsenlänge.
   * Kleinerer Ausschnitt: bleibt im Bild. Größerer: umschließt das Bild. Mittelpunkt immer im Bild.
   */
  function clampCenter(value, half) {
    const a = half;
    const b = 1 - half;
    return Math.min(Math.min(1, Math.max(a, b)), Math.max(Math.max(0, Math.min(a, b)), value));
  }

  class ImageCropper extends HTMLElement {

    connectedCallback() {
      active.add(this);
      this._tries = 0;
      this._setup();
    }

    disconnectedCallback() {
      active.delete(this);
      this.flush();
      if (this._resizeObserver) {
        this._resizeObserver.disconnect();
        this._resizeObserver = null;
      }
      this._ready = false;
    }

    /** Wartet, bis der Server die Kinder angelegt hat und das Bild geladen ist. */
    _setup() {
      const stage = this.querySelector('.image-cropper__stage');
      const img = this.querySelector('.image-cropper__image');
      const frame = this.querySelector('.image-cropper__frame');
      const slider = this.querySelector('.image-cropper__zoom');
      if (!stage || !img || !frame || !slider) {
        if (this._tries++ < 120) {
          requestAnimationFrame(() => this._setup());
        }
        return;
      }
      this._stage = stage;
      this._img = img;
      this._frame = frame;
      this._slider = slider;
      if (img.complete && img.naturalWidth) {
        this._start();
      } else {
        img.addEventListener('load', () => this._start(), { once: true });
      }
    }

    _start() {
      if (this._ready || !this.isConnected) {
        return;
      }
      this._ready = true;
      this._w = this._img.naturalWidth;
      this._h = this._img.naturalHeight;
      const shortSide = Math.min(this._w, this._h);
      const longSide = Math.max(this._w, this._h);
      const minCrop = Number(this.getAttribute('min-crop')) || 1;
      const zoomOut = Number(this.getAttribute('zoom-out')) || 1;
      const maxSize = Number(this.getAttribute('max-size')) || 1;
      this._minSize = Math.min(1, Math.max(1 / MAX_ZOOM, minCrop / shortSide));
      this._maxSize = Math.max(1, Math.min(maxSize, zoomOut * longSide / shortSide));
      this._x = Number(this.getAttribute('crop-x') || 0.5);
      this._y = Number(this.getAttribute('crop-y') || 0.5);
      this._size = Number(this.getAttribute('crop-size') || 1);
      this._clamp();
      this._sent = this._key();

      this._img.style.width = this._w + 'px';
      this._img.style.height = this._h + 'px';
      this._slider.min = '0';
      this._slider.max = '1';
      this._slider.step = 'any';

      if (!this._listening) {
        this._listening = true;
        this._listen();
      }
      this._resizeObserver = new ResizeObserver(() => this._render());
      this._resizeObserver.observe(this._stage);
      this._render();
    }

    // ---------------------------------------------------------------- Geometrie

    /** Bühne in Anzeige-Pixeln: Kantenlänge, Mittelpunkt und Maßstab (Anzeige-Pixel je Bildpixel). */
    _metrics() {
      const stage = this._stage.clientWidth;
      const crop = this._frame.clientWidth || stage;
      return { stage, center: stage / 2, scale: crop / (this._size * Math.min(this._w, this._h)) };
    }

    _clamp() {
      this._size = Math.min(this._maxSize, Math.max(this._minSize, this._size));
      const half = this._size * Math.min(this._w, this._h) / 2;
      this._x = clampCenter(this._x, half / this._w);
      this._y = clampCenter(this._y, half / this._h);
    }

    _render() {
      if (!this._ready) {
        return;
      }
      const { center, scale } = this._metrics();
      const left = center - this._x * this._w * scale;
      const top = center - this._y * this._h * scale;
      this._img.style.transform = `translate(${left}px, ${top}px) scale(${scale})`;
      this._slider.value = String(this._sizeToSlider(this._size));
      this.querySelector('.image-cropper__zoom-in').disabled = this._size <= this._minSize;
      this.querySelector('.image-cropper__zoom-out').disabled = this._size >= this._maxSize;
    }

    /** Regler 0 (ganz heraus) bis 1 (ganz hinein), logarithmisch – jeder Schritt zoomt gleich stark. */
    _sizeToSlider(size) {
      const range = Math.log(this._maxSize / this._minSize);
      return range > 0 ? Math.log(this._maxSize / size) / range : 0;
    }

    _sliderToSize(value) {
      return this._maxSize / Math.pow(this._maxSize / this._minSize, value);
    }

    /** Verschiebt das Bild um dx/dy Anzeige-Pixel (der Ausschnitt wandert entgegengesetzt). */
    _pan(dx, dy) {
      const { scale } = this._metrics();
      this._x -= dx / (scale * this._w);
      this._y -= dy / (scale * this._h);
      this._clamp();
      this._render();
    }

    /** Zoomt so, dass der Bildpunkt unter (ax, ay) – Bühnenkoordinaten – an seiner Stelle bleibt. */
    _zoomTo(size, ax, ay) {
      const before = this._metrics();
      const px = this._x * this._w + (ax - before.center) / before.scale;
      const py = this._y * this._h + (ay - before.center) / before.scale;
      // Über 100 % hinweg gezoomt: dort einmal anhalten. Kleine Schritte weg von 100 % bleiben möglich.
      this._size = (this._size - 1) * (size - 1) < 0 ? 1 : size;
      this._clamp();
      const after = this._metrics();
      this._x = (px - (ax - after.center) / after.scale) / this._w;
      this._y = (py - (ay - after.center) / after.scale) / this._h;
      this._clamp();
      this._render();
    }

    _zoomBy(factor) {
      const { center } = this._metrics();
      this._zoomTo(this._size / factor, center, center);
    }

    _stagePoint(clientX, clientY) {
      const rect = this._stage.getBoundingClientRect();
      return { x: clientX - rect.left, y: clientY - rect.top };
    }

    // ---------------------------------------------------------------- Melden

    _key() {
      return [this._x, this._y, this._size].map((v) => v.toFixed(5)).join(',');
    }

    /** Meldet den Ausschnitt an den Server, falls er sich seit der letzten Meldung geändert hat. */
    flush() {
      clearTimeout(this._timer);
      this._timer = null;
      if (!this._ready || this._key() === this._sent) {
        return;
      }
      this._sent = this._key();
      this.dispatchEvent(new CustomEvent('crop-change', {
        detail: { x: this._x, y: this._y, size: this._size }
      }));
    }

    _flushSoon() {
      clearTimeout(this._timer);
      this._timer = setTimeout(() => this.flush(), NOTIFY_DELAY);
    }

    // ---------------------------------------------------------------- Eingaben

    _listen() {
      const stage = this._stage;
      const pointers = new Map();

      stage.addEventListener('pointerdown', (e) => {
        if (!this._ready || (e.pointerType === 'mouse' && e.button !== 0)) {
          return;
        }
        e.preventDefault();
        stage.focus({ preventScroll: true });
        try {
          stage.setPointerCapture(e.pointerId);
        } catch (ignored) {
          // Zeiger schon weg
        }
        pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
        stage.classList.add('image-cropper__stage--dragging');
      });

      stage.addEventListener('pointermove', (e) => {
        const current = pointers.get(e.pointerId);
        if (!current) {
          return;
        }
        const previous = { x: current.x, y: current.y };
        current.x = e.clientX;
        current.y = e.clientY;
        if (pointers.size === 1) {
          this._pan(current.x - previous.x, current.y - previous.y);
          return;
        }
        // Zwei Finger: um die alte Fingermitte zoomen, dann um die Bewegung der Mitte verschieben.
        const other = Array.from(pointers.values()).find((p) => p !== current);
        const distBefore = Math.hypot(previous.x - other.x, previous.y - other.y);
        const distNow = Math.hypot(current.x - other.x, current.y - other.y);
        if (distBefore < 1 || distNow < 1) {
          return;
        }
        const midBefore = this._stagePoint((previous.x + other.x) / 2, (previous.y + other.y) / 2);
        const midNow = this._stagePoint((current.x + other.x) / 2, (current.y + other.y) / 2);
        this._zoomTo(this._size * distBefore / distNow, midBefore.x, midBefore.y);
        this._pan(midNow.x - midBefore.x, midNow.y - midBefore.y);
      });

      const release = (e) => {
        if (pointers.delete(e.pointerId) && pointers.size === 0) {
          stage.classList.remove('image-cropper__stage--dragging');
          this.flush();
        }
      };
      stage.addEventListener('pointerup', release);
      stage.addEventListener('pointercancel', release);
      stage.addEventListener('lostpointercapture', release);

      stage.addEventListener('wheel', (e) => {
        if (!this._ready) {
          return;
        }
        e.preventDefault(); // Seite bzw. Dialog nicht scrollen
        const perPixel = e.ctrlKey ? 0.01 : 0.0015; // Trackpad-Pinch liefert kleine Werte mit Strg
        const delta = e.deltaMode === 1 ? e.deltaY * 16 : e.deltaY;
        const point = this._stagePoint(e.clientX, e.clientY);
        this._zoomTo(this._size * Math.exp(delta * perPixel), point.x, point.y);
        this._flushSoon();
      }, { passive: false });

      stage.addEventListener('keydown', (e) => {
        if (!this._ready) {
          return;
        }
        const step = this._metrics().stage * KEY_PAN * (e.shiftKey ? 5 : 1);
        switch (e.key) {
          case 'ArrowLeft': this._pan(step, 0); break;
          case 'ArrowRight': this._pan(-step, 0); break;
          case 'ArrowUp': this._pan(0, step); break;
          case 'ArrowDown': this._pan(0, -step); break;
          case '+': case '=': this._zoomBy(ZOOM_STEP); break;
          case '-': case '_': this._zoomBy(1 / ZOOM_STEP); break;
          default: return;
        }
        e.preventDefault();
        this._flushSoon();
      });
      stage.addEventListener('blur', () => this.flush());

      this._slider.addEventListener('input', () => {
        const { center } = this._metrics();
        const value = Number(this._slider.value);
        const size = Math.abs(value - this._sizeToSlider(1)) < SLIDER_SNAP ? 1 : this._sliderToSize(value);
        this._zoomTo(size, center, center);
      });
      this._slider.addEventListener('change', () => this.flush());

      this.addEventListener('click', (e) => {
        const button = e.target.closest && e.target.closest('.image-cropper__zoom-in, .image-cropper__zoom-out');
        if (!button || !this._ready) {
          return;
        }
        this._zoomBy(button.classList.contains('image-cropper__zoom-in') ? ZOOM_STEP : 1 / ZOOM_STEP);
        this.flush();
      });
    }
  }

  customElements.define('cashbox-image-cropper', ImageCropper);
})();
