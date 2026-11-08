/*
 * Sparziele umsortieren: Karte am Griff (.goal-item__handle) nach oben oder unten ziehen.
 *
 * Eigene Umsetzung mit Pointer Events statt HTML5-Drag-and-Drop, weil Letzteres auf vielen
 * Smartphones nicht funktioniert. So geht es mit Maus, Finger und Stift gleich.
 * Auf dem fokussierten Griff verschieben die Pfeiltasten ↑/↓ um eine Position, Esc bricht das Ziehen ab.
 *
 * Verschoben wird nur zwischen Einträgen derselben Sortiergruppe (Attribut data-sort-group, der Status):
 * In der Übersicht stehen aktive, pausierte und erreichte Ziele in einer Liste, ihre Reihenfolge
 * untereinander ist aber fest. "index" ist deshalb die Position innerhalb der Gruppe.
 *
 * Nach dem Ablegen wird auf der Liste das Ereignis "goal-move" ausgelöst
 * (detail: { id, index, keyboard }); Speichern und Neuzeichnen übernimmt der Server (GoalsView).
 */
(function () {
  'use strict';
  if (window.__cashboxGoalSort) {
    return;
  }
  window.__cashboxGoalSort = true;

  const HANDLE = '.goal-item__handle';
  const ITEM = '.goal-item';
  const START_DISTANCE = 4; // px Bewegung, ab der ein Druck auf den Griff als Ziehen gilt
  const EDGE = 64;          // px am oberen/unteren Rand, in denen automatisch gescrollt wird
  const MAX_SCROLL = 18;    // px pro Frame beim automatischen Scrollen

  /** Laufender Ziehvorgang oder null. */
  let drag = null;

  /** Einträge der Liste, die zur selben Sortiergruppe gehören (in Anzeigereihenfolge). */
  function itemsOf(list, group) {
    return Array.from(list.children).filter((el) => el.matches(ITEM) && el.dataset.sortGroup === group);
  }

  function notify(list, item, index, keyboard) {
    list.dispatchEvent(new CustomEvent('goal-move', {
      detail: { id: Number(item.dataset.goalId), index, keyboard }
    }));
  }

  /** Nächster scrollbarer Vorfahre – auch über Shadow-DOM-Grenzen (AppLayout) hinweg. */
  function scrollContainerOf(el) {
    let node = el;
    while (node) {
      node = node.assignedSlot || node.parentElement || (node.getRootNode && node.getRootNode().host) || null;
      if (!node || node === document.body || node === document.documentElement) {
        break;
      }
      const overflowY = getComputedStyle(node).overflowY;
      if ((overflowY === 'auto' || overflowY === 'scroll') && node.scrollHeight > node.clientHeight) {
        return node;
      }
    }
    return document.scrollingElement || document.documentElement;
  }

  function visibleBounds(scroller) {
    if (scroller === document.scrollingElement || scroller === document.documentElement) {
      return { top: 0, bottom: window.innerHeight };
    }
    const rect = scroller.getBoundingClientRect();
    return { top: Math.max(rect.top, 0), bottom: Math.min(rect.bottom, window.innerHeight) };
  }

  // ---------------------------------------------------------------- Ziehen mit Zeiger

  document.addEventListener('pointerdown', (e) => {
    if (drag || !e.isPrimary || (e.pointerType === 'mouse' && e.button !== 0)) {
      return;
    }
    const handle = e.target.closest && e.target.closest(HANDLE);
    const item = handle && handle.closest(ITEM);
    const list = item && item.parentElement;
    if (!list) {
      return;
    }
    e.preventDefault(); // keine Textauswahl beim Ziehen mit der Maus
    try {
      handle.setPointerCapture(e.pointerId); // Ereignisse bleiben beim Griff, auch wenn der Zeiger ihn verlässt
    } catch (ignored) {
      // Zeiger schon wieder weg – dann eben ohne Capture; die Listener hängen am document.
    }
    drag = { handle, item, list, pointerId: e.pointerId, startY: e.clientY, y: e.clientY, active: false };
  });

  document.addEventListener('pointermove', (e) => {
    if (!drag || e.pointerId !== drag.pointerId) {
      return;
    }
    drag.y = e.clientY;
    if (!drag.active) {
      if (Math.abs(drag.y - drag.startY) < START_DISTANCE) {
        return;
      }
      begin();
    }
    e.preventDefault();
    follow();
  });

  document.addEventListener('pointerup', (e) => {
    if (drag && e.pointerId === drag.pointerId) {
      finish(true);
    }
  });

  document.addEventListener('pointercancel', (e) => {
    if (drag && e.pointerId === drag.pointerId) {
      finish(false);
    }
  });

  /** Karte vom Fluss lösen (fixiert unter dem Zeiger) und einen Platzhalter an ihre Stelle setzen. */
  function begin() {
    const { item, list } = drag;
    const rect = item.getBoundingClientRect();
    const placeholder = document.createElement('div');
    placeholder.className = 'goal-item-placeholder';
    placeholder.style.height = rect.height + 'px';

    // Negative Ränder (Zeilen der Übersicht) übernimmt der Platzhalter; die schwebende Karte braucht keine.
    placeholder.style.margin = getComputedStyle(item).margin;

    drag.group = item.dataset.sortGroup;
    drag.originalIndex = itemsOf(list, drag.group).indexOf(item);
    drag.offsetY = drag.startY - rect.top;
    drag.placeholder = placeholder;
    drag.scroller = scrollContainerOf(list);
    drag.active = true;

    list.insertBefore(placeholder, item);
    Object.assign(item.style, {
      position: 'fixed',
      left: rect.left + 'px',
      top: rect.top + 'px',
      width: rect.width + 'px',
      margin: '0',
      zIndex: '1000'
    });
    item.classList.add('goal-item--dragging');
    document.documentElement.classList.add('goal-sorting');
    drag.frame = requestAnimationFrame(autoScroll);
  }

  /**
   * Karte mit dem Zeiger bewegen und den Platzhalter vor die erste Karte der Gruppe setzen, deren Mitte
   * darunter liegt – oder hinter die letzte der Gruppe (nicht ans Listenende: dort folgen andere Gruppen).
   */
  function follow() {
    const { item, list, placeholder, y } = drag;
    item.style.top = (y - drag.offsetY) + 'px';

    const others = itemsOf(list, drag.group).filter((el) => el !== item);
    if (others.length === 0) {
      return;
    }
    const before = others.find((other) => {
      const rect = other.getBoundingClientRect();
      return y < rect.top + rect.height / 2;
    });
    if (before) {
      if (placeholder.nextElementSibling !== before) {
        list.insertBefore(placeholder, before);
      }
    } else {
      const last = others[others.length - 1];
      if (last.nextElementSibling !== placeholder) {
        list.insertBefore(placeholder, last.nextElementSibling);
      }
    }
  }

  /** Scrollt, solange der Zeiger nahe am oberen oder unteren Rand steht (lange Listen, Handy). */
  function autoScroll() {
    if (!drag || !drag.active) {
      return;
    }
    const { top, bottom } = visibleBounds(drag.scroller);
    let step = 0;
    if (drag.y < top + EDGE) {
      step = -MAX_SCROLL * Math.min(1, (top + EDGE - drag.y) / EDGE);
    } else if (drag.y > bottom - EDGE) {
      step = MAX_SCROLL * Math.min(1, (drag.y - (bottom - EDGE)) / EDGE);
    }
    if (step !== 0) {
      const before = drag.scroller.scrollTop;
      drag.scroller.scrollTop += Math.round(step);
      if (drag.scroller.scrollTop !== before) {
        follow();
      }
    }
    drag.frame = requestAnimationFrame(autoScroll);
  }

  /** @param commit true = an der Platzhalter-Stelle ablegen, false = abbrechen */
  function finish(commit) {
    const current = drag;
    drag = null;
    if (!current.active) {
      return; // nur angetippt, nicht gezogen
    }
    cancelAnimationFrame(current.frame);
    const { item, list, placeholder } = current;

    Object.assign(item.style, { position: '', left: '', top: '', width: '', margin: '', zIndex: '' });
    item.classList.remove('goal-item--dragging');
    document.documentElement.classList.remove('goal-sorting');
    if (commit) {
      list.insertBefore(item, placeholder);
    }
    placeholder.remove();

    // Der Klick nach dem Loslassen darf die Karte nicht öffnen.
    const swallowClick = (ev) => {
      ev.preventDefault();
      ev.stopPropagation();
    };
    window.addEventListener('click', swallowClick, { capture: true, once: true });
    setTimeout(() => window.removeEventListener('click', swallowClick, { capture: true }), 0);

    const index = itemsOf(list, current.group).indexOf(item);
    if (commit && index !== current.originalIndex) {
      notify(list, item, index, false);
    }
  }

  // ---------------------------------------------------------------- Tastatur

  document.addEventListener('keydown', (e) => {
    if (drag) {
      if (e.key === 'Escape' && drag.active) {
        e.preventDefault();
        finish(false);
      }
      return;
    }
    if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') {
      return;
    }
    const handle = e.target.closest && e.target.closest(HANDLE);
    const item = handle && handle.closest(ITEM);
    const list = item && item.parentElement;
    if (!list) {
      return;
    }
    const items = itemsOf(list, item.dataset.sortGroup);
    const index = items.indexOf(item);
    const target = e.key === 'ArrowUp' ? index - 1 : index + 1;
    e.preventDefault(); // Seite nicht scrollen
    if (target < 0 || target >= items.length) {
      return;
    }
    list.insertBefore(item, e.key === 'ArrowUp' ? items[target] : items[target].nextSibling);
    handle.focus();
    notify(list, item, target, true);
  });
})();
