import React, { useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";

const POPUP_GAP = 10;
const VIEWPORT_MARGIN = 12;
const MAX_POPOVER_WIDTH = 288;

const InlineHelp = ({ title, description }) => {
    const [open, setOpen] = useState(false);
    const containerRef = useRef(null);
    const triggerRef = useRef(null);
    const popupRef = useRef(null);
    const popupId = useId();
    const [placement, setPlacement] = useState("bottom");
    const [popoverStyle, setPopoverStyle] = useState(null);

    const updatePosition = () => {
        if (!triggerRef.current) {
            return;
        }

        const triggerRect = triggerRef.current.getBoundingClientRect();
        const popupHeight = popupRef.current?.offsetHeight ?? 132;
        const popupWidth = Math.min(MAX_POPOVER_WIDTH, window.innerWidth - (VIEWPORT_MARGIN * 2));
        const spaceBelow = window.innerHeight - triggerRect.bottom - VIEWPORT_MARGIN;
        const spaceAbove = triggerRect.top - VIEWPORT_MARGIN;
        const nextPlacement = spaceBelow >= popupHeight + POPUP_GAP || spaceBelow >= spaceAbove ? "bottom" : "top";

        const top = nextPlacement === "bottom"
            ? Math.min(window.innerHeight - popupHeight - VIEWPORT_MARGIN, triggerRect.bottom + POPUP_GAP)
            : Math.max(VIEWPORT_MARGIN, triggerRect.top - popupHeight - POPUP_GAP);
        const rawLeft = triggerRect.left + (triggerRect.width / 2) - (popupWidth / 2);
        const left = Math.max(VIEWPORT_MARGIN, Math.min(rawLeft, window.innerWidth - popupWidth - VIEWPORT_MARGIN));

        setPlacement(nextPlacement);
        setPopoverStyle({
            top: `${top}px`,
            left: `${left}px`,
            width: `${popupWidth}px`,
        });
    };

    useEffect(() => {
        if (!open) {
            return undefined;
        }

        const handlePointerDown = (event) => {
            if (!containerRef.current?.contains(event.target) && !popupRef.current?.contains(event.target)) {
                setOpen(false);
            }
        };

        const handleKeyDown = (event) => {
            if (event.key === "Escape") {
                setOpen(false);
            }
        };

        document.addEventListener("mousedown", handlePointerDown);
        document.addEventListener("touchstart", handlePointerDown);
        document.addEventListener("keydown", handleKeyDown);
        window.addEventListener("resize", updatePosition);
        window.addEventListener("scroll", updatePosition, true);

        return () => {
            document.removeEventListener("mousedown", handlePointerDown);
            document.removeEventListener("touchstart", handlePointerDown);
            document.removeEventListener("keydown", handleKeyDown);
            window.removeEventListener("resize", updatePosition);
            window.removeEventListener("scroll", updatePosition, true);
        };
    }, [open]);

    useLayoutEffect(() => {
        if (!open) {
            return;
        }

        updatePosition();
    }, [open, title, description]);

    const togglePopover = (event) => {
        event.preventDefault();
        event.stopPropagation();
        setOpen((prev) => !prev);
    };

    const handleKeyToggle = (event) => {
        if (event.key === "Enter" || event.key === " ") {
            togglePopover(event);
        }

        if (event.key === "Escape") {
            event.preventDefault();
            event.stopPropagation();
            setOpen(false);
        }
    };

    return (
        <span className="sc-inline-help" ref={containerRef}>
            <span
                role="button"
                tabIndex={0}
                aria-expanded={open}
                aria-controls={popupId}
                aria-label={title}
                className="sc-inline-help-trigger"
                ref={triggerRef}
                onClick={togglePopover}
                onKeyDown={handleKeyToggle}
            >
                ?
            </span>
            {open && typeof document !== "undefined" && createPortal(
                <span
                    id={popupId}
                    role="dialog"
                    aria-label={title}
                    data-placement={placement}
                    ref={popupRef}
                    className="sc-inline-help-popover"
                    style={popoverStyle ?? undefined}
                    onClick={(event) => event.stopPropagation()}
                >
                    <strong>{title}</strong>
                    <span>{description}</span>
                </span>,
                document.body
            )}
        </span>
    );
};

export default InlineHelp;
