import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from '../../context/LanguageContext.jsx';
import { useWorkspaceTabs } from './WorkspaceTabsContext.jsx';
import './WorkspaceTabStrip.css';

const groupItems = (items) => items.reduce((groups, item) => {
    const group = item.group || 'Bereiche';
    groups[group] = groups[group] || [];
    groups[group].push(item);
    return groups;
}, {});

const ControlIcon = ({ children, viewBox = '0 0 24 24' }) => (
    <svg className="workspace-control-icon" viewBox={viewBox} aria-hidden="true" focusable="false">
        {children}
    </svg>
);

const IconPlus = () => (
    <ControlIcon><path d="M12 5v14M5 12h14" /></ControlIcon>
);

const IconMore = () => (
    <ControlIcon>
        <circle cx="5" cy="12" r="1.4" fill="currentColor" stroke="none" />
        <circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none" />
        <circle cx="19" cy="12" r="1.4" fill="currentColor" stroke="none" />
    </ControlIcon>
);

const IconClose = () => (
    <ControlIcon><path d="m7 7 10 10M17 7 7 17" /></ControlIcon>
);

const IconPin = ({ filled = false }) => (
    <ControlIcon>
        <path
            d="M9 4.5h6l-1 5 3 3v1H7v-1l3-3-1-5Z"
            fill={filled ? 'currentColor' : 'none'}
        />
        <path d="M12 13.5V20" />
    </ControlIcon>
);

const IconSearch = () => (
    <ControlIcon>
        <circle cx="11" cy="11" r="6" />
        <path d="m16 16 4 4" />
    </ControlIcon>
);

const IconChevron = () => (
    <ControlIcon><path d="m7 10 5 5 5-5" /></ControlIcon>
);

const WorkspaceTabStrip = () => {
    const workspace = useWorkspaceTabs();
    const { t } = useTranslation();
    const [launcherOpen, setLauncherOpen] = useState(false);
    const [menuOpen, setMenuOpen] = useState(false);
    const [mobileOpen, setMobileOpen] = useState(false);
    const [query, setQuery] = useState('');
    const [draggedTabId, setDraggedTabId] = useState(null);
    const [dropTargetTabId, setDropTargetTabId] = useState(null);
    const [limitMessage, setLimitMessage] = useState('');
    const launcherRef = useRef(null);
    const mobileLauncherRef = useRef(null);
    const mobileDrawerRef = useRef(null);
    const mobileTriggerRef = useRef(null);
    const desktopLauncherTriggerRef = useRef(null);
    const mobileLauncherTriggerRef = useRef(null);
    const lastLauncherTriggerRef = useRef(null);
    const menuTriggerRef = useRef(null);
    const menuItemRefs = useRef([]);
    const tabButtonRefs = useRef({ desktop: new Map(), mobile: new Map() });

    const filteredItems = useMemo(() => {
        if (!workspace) return [];
        const normalized = query.trim().toLocaleLowerCase();
        if (!normalized) return workspace.launchItems;
        return workspace.launchItems.filter((item) => (
            `${item.label} ${item.description} ${item.group}`.toLocaleLowerCase().includes(normalized)
        ));
    }, [query, workspace]);
    const groupedItems = useMemo(() => groupItems(filteredItems), [filteredItems]);

    useEffect(() => {
        if (!launcherOpen && !menuOpen && !mobileOpen) return undefined;
        const closeOnOutside = (event) => {
            if (launcherOpen) {
                const insideDesktopLauncher = launcherRef.current?.contains(event.target);
                const insideMobileLauncher = mobileLauncherRef.current?.contains(event.target);
                if (!insideDesktopLauncher && !insideMobileLauncher) setLauncherOpen(false);
            }
            if (menuOpen && !launcherRef.current?.contains(event.target)) setMenuOpen(false);
            if (
                mobileOpen
                && !mobileDrawerRef.current?.contains(event.target)
                && !mobileTriggerRef.current?.contains(event.target)
            ) {
                setMobileOpen(false);
            }
        };
        const closeOnEscape = (event) => {
            if (event.key !== 'Escape') return;
            if (launcherOpen) {
                setLauncherOpen(false);
                window.requestAnimationFrame(() => lastLauncherTriggerRef.current?.focus());
            }
            if (menuOpen) {
                setMenuOpen(false);
                window.requestAnimationFrame(() => menuTriggerRef.current?.focus());
            }
            if (mobileOpen) {
                setMobileOpen(false);
                window.requestAnimationFrame(() => mobileTriggerRef.current?.focus());
            }
        };
        document.addEventListener('mousedown', closeOnOutside);
        document.addEventListener('keydown', closeOnEscape);
        return () => {
            document.removeEventListener('mousedown', closeOnOutside);
            document.removeEventListener('keydown', closeOnEscape);
        };
    }, [launcherOpen, menuOpen, mobileOpen]);

    useEffect(() => {
        const handleShortcut = (event) => {
            if ((event.ctrlKey || event.metaKey) && event.shiftKey && event.key.toLowerCase() === 'p') {
                event.preventDefault();
                lastLauncherTriggerRef.current = desktopLauncherTriggerRef.current;
                setLauncherOpen(true);
            }
        };
        window.addEventListener('keydown', handleShortcut);
        return () => window.removeEventListener('keydown', handleShortcut);
    }, []);

    useEffect(() => {
        document.documentElement.classList.toggle('workspace-launcher-open', launcherOpen);
        return () => document.documentElement.classList.remove('workspace-launcher-open');
    }, [launcherOpen]);

    useEffect(() => {
        if (!workspace?.activeTabId) return undefined;
        const frame = window.requestAnimationFrame(() => {
            tabButtonRefs.current.desktop.get(workspace.activeTabId)?.scrollIntoView?.({
                block: 'nearest',
                inline: 'nearest',
            });
        });
        return () => window.cancelAnimationFrame?.(frame);
    }, [workspace?.activeTabId]);

    if (!workspace || !workspace.tabs.length) return null;

    const openItem = (item, options) => {
        const opened = options ? workspace.openRoute(item.url, options) : workspace.openRoute(item.url);
        if (!opened) {
            setLimitMessage(t('workspaceTabs.limitReached', 'Maximal 12 Tabs. Löse oder schließe zuerst einen angehefteten Tab.'));
            return;
        }
        setLauncherOpen(false);
        setMobileOpen(false);
        setLimitMessage('');
        setQuery('');
    };

    const openExtraItem = (event, item) => {
        if (event.button !== 1) return;
        event.preventDefault();
        openItem(item, { forceNew: true });
    };
    const workspaceLabel = workspace.scope === 'pms'
        ? t('workspaceTabs.pmsWorkspace', 'PMS Arbeitsbereiche')
        : t('workspaceTabs.workspace', 'Chrono Arbeitsbereiche');

    const onTabKeyDown = (event, tab, index, mobile) => {
        if (event.key === 'Home' || event.key === 'End') {
            event.preventDefault();
            const nextIndex = event.key === 'Home' ? 0 : workspace.tabs.length - 1;
            const nextTabId = workspace.tabs[nextIndex].id;
            workspace.activateTab(nextTabId);
            window.requestAnimationFrame(() => {
                tabButtonRefs.current[mobile ? 'mobile' : 'desktop'].get(nextTabId)?.focus();
            });
            return;
        }
        const previousKey = event.key === 'ArrowLeft' || (mobile && event.key === 'ArrowUp');
        const nextKey = event.key === 'ArrowRight' || (mobile && event.key === 'ArrowDown');
        if (previousKey || nextKey) {
            event.preventDefault();
            if (event.ctrlKey || event.metaKey || event.shiftKey) {
                workspace.reorderTab(tab.id, index + (previousKey ? -1 : 1));
                return;
            }
            const nextIndex = (index + (previousKey ? -1 : 1) + workspace.tabs.length) % workspace.tabs.length;
            const nextTabId = workspace.tabs[nextIndex].id;
            workspace.activateTab(nextTabId);
            window.requestAnimationFrame(() => {
                tabButtonRefs.current[mobile ? 'mobile' : 'desktop'].get(nextTabId)?.focus();
            });
        }
        if ((event.key === 'Delete' || event.key === 'Backspace') && !tab.pinned) {
            event.preventDefault();
            const fallbackTab = workspace.tabs[index + 1] || workspace.tabs[index - 1];
            workspace.closeTab(tab.id);
            if (fallbackTab) {
                window.requestAnimationFrame(() => {
                    tabButtonRefs.current[mobile ? 'mobile' : 'desktop'].get(fallbackTab.id)?.focus();
                });
            }
        }
    };

    const onMenuKeyDown = (event) => {
        if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
        const enabledItems = menuItemRefs.current.filter((item) => item && !item.disabled);
        if (!enabledItems.length) return;
        event.preventDefault();
        const currentIndex = Math.max(0, enabledItems.indexOf(document.activeElement));
        let nextIndex = currentIndex;
        if (event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % enabledItems.length;
        if (event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + enabledItems.length) % enabledItems.length;
        if (event.key === 'Home') nextIndex = 0;
        if (event.key === 'End') nextIndex = enabledItems.length - 1;
        enabledItems[nextIndex].focus();
    };

    const trapModalFocus = (event) => {
        if (event.key !== 'Tab') return;
        const focusable = [...event.currentTarget.querySelectorAll(
            'button:not([disabled]), input:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'
        )].filter((element) => !element.hasAttribute('hidden'));
        if (!focusable.length) return;
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    };

    const tabList = (mobile = false) => (
        <div className={mobile ? 'workspace-mobile-list' : 'workspace-tab-list'} role="tablist" aria-label={t('workspaceTabs.ariaLabel', 'Offene Arbeitsbereiche')}>
            {workspace.tabs.map((tab, index) => (
                <div
                    className={`workspace-tab${tab.id === workspace.activeTabId ? ' is-active' : ''}${tab.pinned ? ' is-pinned' : ''}${draggedTabId === tab.id ? ' is-dragging' : ''}${dropTargetTabId === tab.id ? ' is-drop-target' : ''}`}
                    key={tab.id}
                    draggable={!mobile}
                    onDragStart={(event) => {
                        if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move';
                        setDraggedTabId(tab.id);
                    }}
                    onDragEnd={() => {
                        setDraggedTabId(null);
                        setDropTargetTabId(null);
                    }}
                    onDragEnter={() => {
                        if (draggedTabId && draggedTabId !== tab.id) setDropTargetTabId(tab.id);
                    }}
                    onDragLeave={(event) => {
                        if (!event.relatedTarget || !(event.relatedTarget instanceof Node) || !event.currentTarget.contains(event.relatedTarget)) {
                            setDropTargetTabId(null);
                        }
                    }}
                    onDragOver={(event) => {
                        event.preventDefault();
                        if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
                    }}
                    onDrop={() => {
                        if (draggedTabId) workspace.reorderTab(draggedTabId, index);
                        setDraggedTabId(null);
                        setDropTargetTabId(null);
                    }}
                >
                    <button
                        type="button"
                        className="workspace-tab-main"
                        role="tab"
                        aria-selected={tab.id === workspace.activeTabId}
                        tabIndex={tab.id === workspace.activeTabId ? 0 : -1}
                        title={tab.title}
                        ref={(node) => {
                            const refs = tabButtonRefs.current[mobile ? 'mobile' : 'desktop'];
                            if (node) refs.set(tab.id, node);
                            else refs.delete(tab.id);
                        }}
                        onClick={() => {
                            workspace.activateTab(tab.id);
                            if (mobile) setMobileOpen(false);
                        }}
                        onMouseDown={(event) => { if (event.button === 1) event.preventDefault(); }}
                        onAuxClick={(event) => {
                            if (event.button !== 1) return;
                            event.preventDefault();
                            if (!tab.pinned) workspace.closeTab(tab.id);
                        }}
                        onKeyDown={(event) => onTabKeyDown(event, tab, index, mobile)}
                    >
                        <span className="workspace-tab-icon" aria-hidden="true">{tab.icon}</span>
                        <span className="workspace-tab-title">{tab.title}</span>
                    </button>
                    <button
                        type="button"
                        className="workspace-tab-pin"
                        onClick={() => workspace.togglePinned(tab.id)}
                        title={`${tab.pinned ? t('workspaceTabs.unpin', 'Tab lösen') : t('workspaceTabs.pin', 'Tab anheften')}: ${tab.title}`}
                        aria-label={`${tab.pinned ? t('workspaceTabs.unpin', 'Tab lösen') : t('workspaceTabs.pin', 'Tab anheften')}: ${tab.title}`}
                        aria-pressed={tab.pinned}
                    >
                        <IconPin filled={tab.pinned} />
                    </button>
                    {!tab.pinned && (
                        <button
                            type="button"
                            className="workspace-tab-close"
                            onClick={() => workspace.closeTab(tab.id)}
                            title={t('workspaceTabs.close', 'Tab schließen')}
                            aria-label={`${t('workspaceTabs.close', 'Tab schließen')}: ${tab.title}`}
                        ><IconClose /></button>
                    )}
                </div>
            ))}
        </div>
    );

    return (
        <section className="workspace-tabs" aria-label={workspaceLabel}>
            <div className="workspace-tabs-desktop">
                {tabList(false)}
                <div className="workspace-tab-toolbar" ref={launcherRef}>
                    <button
                        type="button"
                        className="workspace-tab-add"
                        ref={desktopLauncherTriggerRef}
                        onClick={() => {
                            lastLauncherTriggerRef.current = desktopLauncherTriggerRef.current;
                            setLauncherOpen((open) => !open);
                            setMenuOpen(false);
                            setLimitMessage('');
                        }}
                        aria-expanded={launcherOpen}
                        aria-haspopup="dialog"
                        aria-controls="workspace-launcher-desktop"
                        aria-label={t('workspaceTabs.open', 'Arbeitsbereich öffnen')}
                        title={`${t('workspaceTabs.open', 'Arbeitsbereich öffnen')} (Ctrl+Shift+P)`}
                    ><IconPlus /></button>
                    <button
                        type="button"
                        className="workspace-tab-more"
                        ref={menuTriggerRef}
                        onClick={() => { setMenuOpen((open) => !open); setLauncherOpen(false); }}
                        aria-expanded={menuOpen}
                        aria-haspopup="menu"
                        aria-controls="workspace-tab-actions"
                        aria-label={t('workspaceTabs.more', 'Tab-Aktionen')}
                    ><IconMore /></button>

                    {launcherOpen && (
                        <div id="workspace-launcher-desktop" className="workspace-launcher" role="dialog" aria-label={t('workspaceTabs.open', 'Arbeitsbereich öffnen')}>
                            <label className="workspace-launcher-search">
                                <IconSearch />
                                <input
                                    autoFocus
                                    type="search"
                                    value={query}
                                    onChange={(event) => setQuery(event.target.value)}
                                    placeholder={t('workspaceTabs.search', 'Bereich suchen …')}
                                    aria-label={t('workspaceTabs.search', 'Bereich suchen …')}
                                />
                                <kbd aria-hidden="true">Esc</kbd>
                            </label>
                            <div className="workspace-launcher-results">
                                {Object.entries(groupedItems).map(([group, items]) => (
                                    <section key={group}>
                                        <h3>{group}</h3>
                                        {items.map((item) => (
                                            <button type="button" key={item.key} onClick={() => openItem(item)}
                                                onMouseDown={(event) => { if (event.button === 1) event.preventDefault(); }}
                                                onAuxClick={(event) => openExtraItem(event, item)}
                                                title={t('workspaceTabs.middleClick', 'Mittelklick öffnet einen weiteren Tab')}>
                                                <span>{item.icon}</span>
                                                <span><strong>{item.label}</strong><small>{item.description}</small></span>
                                            </button>
                                        ))}
                                    </section>
                                ))}
                                {!filteredItems.length && <p>{t('workspaceTabs.noResults', 'Kein passender Bereich gefunden.')}</p>}
                                {limitMessage && <p className="workspace-limit-message" role="alert">{limitMessage}</p>}
                            </div>
                        </div>
                    )}

                    {menuOpen && (
                        <div id="workspace-tab-actions" className="workspace-tab-menu" role="menu" aria-label={t('workspaceTabs.more', 'Tab-Aktionen')} onKeyDown={onMenuKeyDown}>
                            <button ref={(node) => { menuItemRefs.current[0] = node; }} autoFocus type="button" role="menuitem" onClick={() => { workspace.closeOtherTabs(workspace.activeTabId); setMenuOpen(false); menuTriggerRef.current?.focus(); }}>
                                {t('workspaceTabs.closeOthers', 'Andere Tabs schließen')}
                            </button>
                            <button ref={(node) => { menuItemRefs.current[1] = node; }} type="button" role="menuitem" onClick={() => { workspace.closeTabsToRight(workspace.activeTabId); setMenuOpen(false); menuTriggerRef.current?.focus(); }}>
                                {t('workspaceTabs.closeRight', 'Tabs rechts schließen')}
                            </button>
                            <button ref={(node) => { menuItemRefs.current[2] = node; }} type="button" role="menuitem" disabled={!workspace.recentlyClosed.length} onClick={() => { workspace.restoreLastClosed(); setMenuOpen(false); menuTriggerRef.current?.focus(); }}>
                                {t('workspaceTabs.restore', 'Geschlossenen Tab wiederherstellen')}
                            </button>
                        </div>
                    )}
                </div>
            </div>

            <div className="workspace-tabs-mobile">
                <button
                    type="button"
                    className="workspace-mobile-trigger"
                    ref={mobileTriggerRef}
                    onClick={() => setMobileOpen((open) => !open)}
                    aria-expanded={mobileOpen}
                    aria-controls="workspace-mobile-drawer"
                    aria-label={`${workspaceLabel}: ${workspace.activeTab?.title}`}
                >
                    <span>{workspace.activeTab?.icon}</span>
                    <strong>{workspace.activeTab?.title}</strong>
                    <small>{workspace.tabs.length}</small>
                    <span className={`workspace-mobile-chevron${mobileOpen ? ' is-open' : ''}`}><IconChevron /></span>
                </button>
                <button
                    type="button"
                    className="workspace-mobile-add"
                    ref={mobileLauncherTriggerRef}
                    onClick={() => {
                        lastLauncherTriggerRef.current = mobileLauncherTriggerRef.current;
                        setLauncherOpen(true);
                        setMobileOpen(false);
                        setLimitMessage('');
                    }}
                    aria-haspopup="dialog"
                    aria-controls="workspace-launcher-mobile"
                    aria-label={t('workspaceTabs.open', 'Arbeitsbereich öffnen')}
                ><IconPlus /></button>
                {mobileOpen && (
                    <div id="workspace-mobile-drawer" ref={mobileDrawerRef} className="workspace-mobile-drawer" role="region" aria-label={t('workspaceTabs.ariaLabel', 'Offene Arbeitsbereiche')}>
                        {tabList(true)}
                        <button type="button" onClick={() => { workspace.restoreLastClosed(); setMobileOpen(false); }} disabled={!workspace.recentlyClosed.length}>
                            {t('workspaceTabs.restore', 'Geschlossenen Tab wiederherstellen')}
                        </button>
                    </div>
                )}
                {launcherOpen && (
                    <div id="workspace-launcher-mobile" ref={mobileLauncherRef} className="workspace-mobile-launcher" role="dialog" aria-modal="true" aria-label={t('workspaceTabs.open', 'Arbeitsbereich öffnen')} onKeyDown={trapModalFocus}>
                        <div className="workspace-mobile-launcher-head">
                            <label className="workspace-launcher-search">
                                <IconSearch />
                                <input autoFocus type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('workspaceTabs.search', 'Bereich suchen …')} aria-label={t('workspaceTabs.search', 'Bereich suchen …')} />
                            </label>
                            <button type="button" onClick={() => { setLauncherOpen(false); lastLauncherTriggerRef.current?.focus(); }} aria-label={t('close', 'Schließen')}><IconClose /></button>
                        </div>
                        <div className="workspace-launcher-results">
                            {Object.entries(groupedItems).map(([group, items]) => (
                                <section key={group}>
                                    <h3>{group}</h3>
                                    {items.map((item) => <button type="button" key={item.key} onClick={() => openItem(item)}
                                        onMouseDown={(event) => { if (event.button === 1) event.preventDefault(); }}
                                        onAuxClick={(event) => openExtraItem(event, item)}><span>{item.icon}</span><strong>{item.label}</strong></button>)}
                                </section>
                            ))}
                            {limitMessage && <p className="workspace-limit-message" role="alert">{limitMessage}</p>}
                        </div>
                    </div>
                )}
            </div>
        </section>
    );
};

export default WorkspaceTabStrip;
