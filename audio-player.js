/* Bound native media resources for long sessions. Cosmetic sounds may be dropped
 * when all voices are busy; never allocate a new decoder for every poker action. */
(function () {
    'use strict';
    var voices = [], limit = 12;
    window.rrPlayAudio = function (url, volume, rate) {
        var voice = voices.find(function (v) { return v.url === url && (v.audio.paused || v.audio.ended); });
        if (!voice && voices.length < limit) {
            voice = { url: url, audio: new Audio(url) };
            voice.audio.preload = 'auto';
            voices.push(voice);
        }
        if (!voice) voice = voices.find(function (v) { return v.audio.paused || v.audio.ended; });
        if (!voice) return;
        try {
            if (voice.url !== url) { voice.audio.src = url; voice.url = url; }
            voice.audio.currentTime = 0;
            voice.audio.volume = volume;
            voice.audio.playbackRate = rate;
            var p = voice.audio.play();
            if (p && p.catch) p.catch(function () {});
        } catch (_) { /* Audio is optional, including when Safari blocks playback. */ }
    };
    function stop() {
        voices.forEach(function (v) { v.audio.pause(); v.audio.removeAttribute('src'); v.audio.load(); });
        voices = [];
    }
    window.addEventListener('pagehide', stop);
    document.addEventListener('visibilitychange', function () { if (document.hidden) stop(); });
})();
