/* Pure navigation and speech lifecycle; shared by the page and Node tests. */
(function (root, factory) {
  const api = factory();
  if (typeof module === 'object' && module.exports) module.exports = api;
  else root.ZipaiPresentation = api;
})(typeof globalThis !== 'undefined' ? globalThis : this, function () {
  'use strict';
  function slideIndex(slides, hash) {
    let id;
    try { id = decodeURIComponent(String(hash || '').replace(/^#/, '')); } catch (_) { return 0; }
    const index = slides.findIndex(slide => slide.id === id);
    return index < 0 ? 0 : index;
  }
  function koreanVoices(voices) { return voices.filter(v => /^ko(?:[-_]|$)/i.test(v.lang || '')); }
  function chunks(text) {
    return String(text).match(/[^.!?。]+[.!?。]?/g)?.map(s => s.trim()).filter(Boolean) || [];
  }
  class Narrator {
    constructor(synth, Utterance, onState, onEnd) {
      this.synth = synth; this.Utterance = Utterance;
      this.onState = onState; this.onEnd = onEnd;
      this.token = 0; this.active = false; this.paused = false;
    }
    stop() {
      ++this.token; this.active = false; this.paused = false;
      if (this.synth) this.synth.cancel();
      this.onState('idle');
    }
    speak(text, voice, rate) {
      this.stop();
      if (!this.synth || !this.Utterance) { this.onState('unsupported'); return false; }
      if (!voice || !/^ko(?:[-_]|$)/i.test(voice.lang || '')) { this.onState('no-voice'); return false; }
      const parts = chunks(text); if (!parts.length) return false;
      const token = this.token; let index = 0;
      this.active = true; this.synth.resume();
      const enqueue = () => {
        if (token !== this.token || !this.active) return;
        if (index === parts.length) {
          this.active = false; this.paused = false;
          this.onState('finished'); this.onEnd(); return;
        }
        const utterance = new this.Utterance(parts[index++]);
        utterance.lang = 'ko-KR'; utterance.voice = voice; utterance.rate = rate;
        utterance.onstart = () => { if (token === this.token) this.onState('speaking'); };
        utterance.onend = enqueue;
        utterance.onerror = event => {
          if (token !== this.token) return;
          this.active = false; this.paused = false;
          this.onState(event.error === 'not-allowed' ? 'blocked' : 'error');
        };
        this.synth.speak(utterance);
      };
      enqueue(); return true;
    }
    togglePause() {
      if (!this.active) return;
      this.paused = !this.paused;
      if (this.paused) { this.synth.pause(); this.onState('paused'); }
      else { this.synth.resume(); this.onState('speaking'); }
    }
  }
  // Time controls progression independently of browser speech callbacks.
  class TimedTour {
    constructor(scenes, onSlide, onFinish, clock = {}) {
      this.scenes = scenes; this.onSlide = onSlide; this.onFinish = onFinish;
      this.now = clock.now || (() => Date.now());
      this.setTimer = clock.setTimeout || ((fn, ms) => setTimeout(fn, ms));
      this.clearTimer = clock.clearTimeout || (id => clearTimeout(id));
      this.active = false; this.paused = false; this.position = 0; this.generation = 0;
    }
    start() { this.stop(); this.active = true; this.begin(0); }
    begin(position) {
      this.clearTimer(this.timer); ++this.generation;
      this.position = position; this.paused = false; this.voiceEnded = false; this.timeEnded = false;
      this.remaining = this.scenes[position].seconds * 1000;
      this.arm(); this.onSlide(this.scenes[position]);
    }
    arm() {
      const generation = this.generation;
      this.deadline = this.now() + this.remaining;
      this.timer = this.setTimer(() => {
        if (!this.active || this.paused || generation !== this.generation) return;
        this.timeEnded = true; this.remaining = 0; this.maybeAdvance();
      }, this.remaining);
    }
    voiceDone() { if (!this.active) return; this.voiceEnded = true; this.maybeAdvance(); }
    maybeAdvance() {
      if (!this.active || this.paused || !this.timeEnded) return;
      if (this.position + 1 === this.scenes.length) { this.stop(); this.onFinish(); }
      else this.begin(this.position + 1);
    }
    pause() {
      if (!this.active || this.paused) return;
      this.remaining = this.timeEnded ? 0 : Math.max(0, this.deadline - this.now());
      this.clearTimer(this.timer); this.paused = true; ++this.generation;
    }
    resume() {
      if (!this.active || !this.paused) return;
      this.paused = false; this.arm(); this.maybeAdvance();
    }
    move(delta) {
      if (!this.active) return;
      const position = Math.max(0, Math.min(this.scenes.length - 1, this.position + delta));
      if (position !== this.position) this.begin(position);
    }
    remainingSeconds() {
      return Math.ceil((this.paused ? this.remaining : Math.max(0, this.deadline - this.now())) / 1000);
    }
    stop() { this.clearTimer(this.timer); ++this.generation; this.active = false; this.paused = false; }
  }
  function fitScale(viewWidth, viewHeight, contentWidth, contentHeight) {
    if ([viewWidth, viewHeight, contentWidth, contentHeight].some(value => !Number.isFinite(value) || value <= 0)) return 1;
    return Math.min(1, Math.max(1, viewWidth - 20) / contentWidth, Math.max(1, viewHeight - 20) / contentHeight);
  }
  return { slideIndex, koreanVoices, Narrator, TimedTour, fitScale };
});
