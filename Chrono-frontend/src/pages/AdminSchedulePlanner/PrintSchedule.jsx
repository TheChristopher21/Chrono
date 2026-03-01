// src/pages/admin/PrintSchedule.jsx
import React, { useEffect, useMemo, useRef } from 'react';
import { useLocation } from 'react-router-dom';
import { addDays, format, formatISO, startOfWeek } from 'date-fns';
import { de } from 'date-fns/locale';
import api from '../../utils/api';
import jsPDF from 'jspdf';

// ------------------ Utils & Config ------------------
function useQueryParams() { return new URLSearchParams(useLocation().search); }
const DAYS_FULL = ['Montag','Dienstag','Mittwoch','Donnerstag','Freitag','Samstag','Sonntag'];

const L = {
    margin: 8, weekGap: 6, colGap: 3,
    dayPad: 2.8, radius: 2.2,
    headerH: 8, shiftGap: 2.2,
    labelH: 5, chipH: 5.8,
    chipGap: 1.6, chipVGap: 1.4,
    weekTitleH: 9, weekTitlePadX: 5
};

// API
const fetchUsers = async () => (await api.get('/api/admin/users')).data ?? [];
const fetchShifts = async () => (await api.get('/api/admin/shift-definitions')).data ?? [];
const fetchScheduleRange = async (startDate, endDate) => {
    const start = formatISO(startDate, { representation: 'date' });
    const end   = formatISO(endDate,   { representation: 'date' });
    const { data } = await api.get('/api/admin/schedule', { params: { start, end } });
    const map = {};
    (Array.isArray(data) ? data : []).forEach(e => {
        const k = formatISO(new Date(e.date), { representation: 'date' });
        (map[k] ||= []).push(e);
    });
    return map;
};

// Theme helpers
function cssVar(name, fallback) {
    try { return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback; }
    catch { return fallback; }
}
function hexToRgb(hex){ const h=(hex||'').replace('#',''); if(h.length!==6) return {r:0,g:0,b:0};
    return { r:parseInt(h.slice(0,2),16), g:parseInt(h.slice(2,4),16), b:parseInt(h.slice(4,6),16) };
}
function luminance({r,g,b}){ return (0.2126*r + 0.7152*g + 0.0722*b)/255; }
function textColorForHex(hex, dark='#111', light='#fff'){ return luminance(hexToRgb(hex)) > 0.55 ? dark : light; }

function readTheme(force) {
    const forced=(force||'').toLowerCase();
    const isDark = forced ? forced==='dark' : (()=>{ try{
        const bg = cssVar('--ud-c-bg','') || getComputedStyle(document.body).backgroundColor || '#fff';
        const m = /rgb\((\d+),\s*(\d+),\s*(\d+)\)/.exec(bg);
        if(!m) return false;
        const Lm = (0.2126*+m[1] + 0.7152*+m[2] + 0.0722*+m[3])/255;
        return Lm < 0.5;
    }catch{return false;}})();

    const primary = cssVar('--ud-c-primary', isDark ? '#6aa6ff' : '#3b82f6');

    if (isDark) return {
        isDark: true,
        pageBg:   hexToRgb('#0e1320'),
        titleBg:  hexToRgb('#1a2237'),
        titleText:hexToRgb('#eaf1ff'),
        cardBg:   hexToRgb('#151b2c'),
        cardBorder: hexToRgb('#2a3554'),
        headerBg: hexToRgb('#1d2742'),
        headerText: hexToRgb('#f3f6ff'),
        mutedText: hexToRgb('#c6cee4'),
        labelText: hexToRgb('#ffffff'),
        chipDefaultBg: primary,
        chipDefaultFg: textColorForHex(primary)
    };

    return {
        isDark: false,
        pageBg:   hexToRgb('#ffffff'),
        titleBg:  hexToRgb('#eef2f7'),
        titleText:hexToRgb('#0b1220'),
        cardBg:   hexToRgb('#ffffff'),
        cardBorder: hexToRgb('#cbd5e1'),
        headerBg: hexToRgb('#f3f4f6'),
        headerText: hexToRgb('#111827'),
        mutedText: hexToRgb('#374151'),
        labelText: hexToRgb('#0b1220'),
        chipDefaultBg: primary,
        chipDefaultFg: textColorForHex(primary, '#111', '#fff')
    };
}

// Chip-Layout
function layoutChips(pdf, items, maxW, fs=8.5){
    pdf.setFontSize(fs);
    const rows=[]; let row=[], x=0;
    items.forEach(it=>{
        const w = Math.max(pdf.getTextWidth(it.text) + 5.5, 14);
        if (x>0 && x+w>maxW) { rows.push(row); row=[]; x=0; }
        row.push({ ...it, w }); x += w + L.chipGap;
    });
    if (row.length) rows.push(row);
    const totalH = rows.length ? rows.length*L.chipH + (rows.length-1)*L.chipVGap : L.chipH;
    return { rows, totalH };
}
function measureShift(pdf, chips, contentW){ return L.labelH + 1.6 + layoutChips(pdf, chips, contentW).totalH; }
function measureDay(pdf, shifts, contentW){ let y=L.dayPad + L.headerH + L.shiftGap; shifts.forEach(s=>y+=measureShift(pdf,s.chips,contentW)+L.shiftGap); return y + L.dayPad; }

// Drawing helpers
function roundRect(pdf,x,y,w,h,r,fill,stroke,lineW=0.2){
    if (fill) pdf.setFillColor(fill.r,fill.g,fill.b);
    if (stroke){ pdf.setDrawColor(stroke.r,stroke.g,stroke.b); pdf.setLineWidth(lineW); }
    pdf.roundedRect(x,y,w,h,r,r, fill&&stroke?'DF' : fill?'F' : stroke?'S':'S');
}
function drawLR(pdf,x,y,w,left,right,size,color){
    pdf.setFontSize(size); pdf.setTextColor(color.r,color.g,color.b);
    if (left) pdf.text(left, x, y);
    if (right){ const tw=pdf.getTextWidth(right); pdf.text(right, x + w - tw, y); }
}
function drawChips(pdf,x,y,maxW,rows){
    rows.forEach((row,i)=>{
        let cx=x, cy=y + i*(L.chipH + L.chipVGap);
        row.forEach(chip=>{
            const r=L.radius*0.8;
            const bg=hexToRgb(chip.bg || '#3b82f6');
            const fg=hexToRgb(chip.fg || textColorForHex(chip.bg || '#3b82f6'));
            pdf.setFillColor(bg.r,bg.g,bg.b);
            pdf.setDrawColor(0,0,0);
            pdf.setLineWidth(0.2);
            pdf.roundedRect(cx,cy,chip.w,L.chipH,r,r,'DF');
            pdf.setTextColor(fg.r,fg.g,fg.b);
            pdf.setFontSize(8.5);
            pdf.text(chip.text, cx+2.3, cy + L.chipH*0.68);
            cx += chip.w + L.chipGap;
        });
    });
}

function drawWeek(pdf,x,y,w,theme,week){
    // Titel
    roundRect(pdf,x,y,w,L.weekTitleH,L.radius,theme.titleBg,theme.cardBorder,0.25);
    pdf.setFontSize(10); pdf.setTextColor(theme.titleText.r,theme.titleText.g,theme.titleText.b);
    pdf.text(week.title, x + L.weekTitlePadX, y + L.weekTitleH*0.68);

    const top = y + L.weekTitleH + 3;
    const colW = (w - 6*L.colGap) / 7;
    const contentW = colW - 2*L.dayPad;

    // einheitliche Kartenhöhe
    const heights = week.days.map(d => measureDay(pdf, d.shifts, contentW));
    const cardH = Math.max(...heights);

    for (let i=0; i<7; i++){
        const cx = x + i*(colW + L.colGap);
        // Karte
        roundRect(pdf, cx, top, colW, cardH, L.radius, theme.cardBg, theme.cardBorder, 0.25);
        // Header-Balken
        pdf.setFillColor(theme.headerBg.r,theme.headerBg.g,theme.headerBg.b);
        pdf.roundedRect(cx+0.2, top+0.2, colW-0.4, L.headerH, L.radius*0.7, L.radius*0.7, 'F');
        // Header-Text
        drawLR(pdf, cx+L.dayPad, top+5.2, colW - 2*L.dayPad, week.days[i].name, week.days[i].date, 8.6, theme.headerText);

        let cy = top + L.dayPad + L.headerH + L.shiftGap;
        week.days[i].shifts.forEach(s=>{
            drawLR(pdf, cx+L.dayPad, cy+3.8, contentW, s.label, s.time, 8.6, theme.labelText);
            cy += L.labelH + 1.6;
            const { rows } = layoutChips(pdf, s.chips, contentW);
            drawChips(pdf, cx+L.dayPad, cy, contentW, rows);
            const blockH = rows.length ? rows.length*L.chipH + (rows.length-1)*L.chipVGap : L.chipH;
            cy += blockH + L.shiftGap;
        });
    }
    return L.weekTitleH + 3 + cardH;
}

// ------------------ Component ------------------
export default function PrintSchedule(){
    const ranRef = useRef(false); // StrictMode-Guard
    const q = useQueryParams();
    const startParam = q.get('start');
    const orientation = (q.get('orientation') || 'landscape').toLowerCase();
    const forceTheme = q.get('forceTheme'); // optional
    const defaultWeeks = parseInt(q.get('weeks') || '', 10);

    const startDate = useMemo(
        () => startOfWeek(startParam ? new Date(startParam) : new Date(), { weekStartsOn: 1 }),
        [startParam]
    );

    useEffect(() => {
        if (ranRef.current) return;
        ranRef.current = true;

        (async () => {
            // 1) Wochenzahl
            let weeks = Number.isFinite(defaultWeeks) && defaultWeeks > 0 ? defaultWeeks : undefined;
            if (!weeks) {
                const ask = window.prompt('Wie viele Wochen sollen exportiert werden?', '1');
                if (ask === null) return;
                weeks = Math.max(1, parseInt(ask, 10) || 1);
            }

            // 2) Daten laden
            const endDate = addDays(startDate, weeks*7 - 1);
            const [usersRaw, shiftsRaw, map] = await Promise.all([
                fetchUsers(), fetchShifts(), fetchScheduleRange(startDate, endDate)
            ]);
            const users = Array.isArray(usersRaw) ? usersRaw : [];
            const shifts = (shiftsRaw || []).filter(s => s.isActive);

            // 3) PDF + Theme
            const theme = readTheme(forceTheme);
            const pdf = new jsPDF({ orientation, unit: 'mm', format: 'a4' });
            const pageW = pdf.internal.pageSize.getWidth();
            const pageH = pdf.internal.pageSize.getHeight();
            if (theme.isDark) { pdf.setFillColor(theme.pageBg.r,theme.pageBg.g,theme.pageBg.b); pdf.rect(0,0,pageW,pageH,'F'); }
            const contentW = pageW - 2*L.margin;

            // 4) Wochen-Daten vorbereiten
            const weeksData = [];
            for (let w=0; w<weeks; w++){
                const ws = addDays(startOfWeek(startDate, { weekStartsOn: 1 }), w*7);
                const title = `KW ${format(ws,'w')} / ${format(ws,'yyyy')}`;
                const days = DAYS_FULL.map((name, i) => {
                    const key = formatISO(addDays(ws, i), { representation: 'date' });
                    const shiftsForDay = shifts.map(s => {
                        const entries = (map[key] || []).filter(e => e.shift === s.shiftKey);
                        const chips = entries.map(e => {
                            const u = users.find(x => x.id === e.userId);
                            if (!u) return null;
                            const bg = u.color ? (u.color.startsWith('#') ? u.color : `#${u.color}`) : cssVar('--ud-c-primary', '#3b82f6');
                            const fg = textColorForHex(bg, '#111', '#fff');
                            return { text: `${u.firstName} ${u.lastName}`, bg, fg };
                        }).filter(Boolean);
                        if (!chips.length) chips.push({ text: '–', bg: '#9aa3b2', fg: '#111' });
                        return { label: s.label || '', time: `${s.startTime || ''}${s.endTime ? ' - ' + s.endTime : ''}`.trim(), chips };
                    });
                    return { name, date: format(new Date(key), 'dd.MM.', { locale: de }), shifts: shiftsForDay };
                });
                weeksData.push({ title, days });
            }

            // 5) Höhen messen & Seiten packen
            const colW = (contentW - 6*L.colGap)/7;
            const contentWDay = colW - 2*L.dayPad;
            const weekHeights = weeksData.map(week => {
                let maxH = 0;
                for (let i=0;i<7;i++) maxH = Math.max(maxH, measureDay(pdf, week.days[i].shifts, contentWDay));
                return L.weekTitleH + 3 + maxH;
            });

            let i = 0;
            while (i < weeksData.length) {
                let y = L.margin;
                const availH = pageH - 2*L.margin;
                while (i < weeksData.length && (y + weekHeights[i]) <= (L.margin + availH + 0.1)) {
                    const used = drawWeek(pdf, L.margin, y, contentW, {
                        titleBg: readTheme(forceTheme).titleBg, titleText: readTheme(forceTheme).titleText,
                        cardBg: readTheme(forceTheme).cardBg, cardBorder: readTheme(forceTheme).cardBorder,
                        headerBg: readTheme(forceTheme).headerBg, headerText: readTheme(forceTheme).headerText,
                        mutedText: readTheme(forceTheme).mutedText, labelText: readTheme(forceTheme).labelText
                    }, weeksData[i]);
                    y += used + L.weekGap;
                    i++;
                }
                // Footer
                pdf.setFontSize(8); pdf.setTextColor(readTheme(forceTheme).isDark ? 255 : 0);
                pdf.text(`Erstellt: ${format(new Date(),'dd.MM.yyyy HH:mm',{locale:de})}`, L.margin, pageH - 4);

                if (i < weeksData.length) {
                    pdf.addPage('a4', orientation);
                    if (readTheme(forceTheme).isDark) { pdf.setFillColor(readTheme(forceTheme).pageBg.r,readTheme(forceTheme).pageBg.g,readTheme(forceTheme).pageBg.b); pdf.rect(0,0,pageW,pageH,'F'); }
                }
            }

            // 6) Genau eine Öffnung der PDF
            const url = pdf.output('bloburl');

            // WICHTIG: kein 'noopener,noreferrer' hier, sonst liefert Chrome/Edge oft null trotz geöffnetem Tab
            let opened = false;
            try {
                const w = window.open(url, '_blank');
                opened = !!w;
            } catch { /* ignore */ }

            if (!opened) {
                // Pop-ups blockiert: nur im gleichen Tab öffnen
                window.location.href = url;
            }
        })();
    }, [defaultWeeks, forceTheme, orientation, startDate]);

    // Headless: kein UI
    return null;
}
