import React, { useEffect, useRef } from 'react';
import PropTypes from 'prop-types';
import useModalOpen from '../utils/useModalOpen';

const ModalOverlay = ({
  visible = true,
  children,
  className = '',
  onClose,
  onClick,
  ...props
}) => {
  const overlayRef = useRef(null);
  useModalOpen(visible);

  // iOS shrinks the visual viewport for the keyboard, not the layout viewport.
  // Keep mobile sheets in the visible area; leave pinch zoom to the browser.
  useEffect(() => {
    const viewport = window.visualViewport;
    const overlay = overlayRef.current;
    if (!visible || !viewport || !overlay) return undefined;
    const update = () => {
      if (Math.abs(viewport.scale - 1) > 0.01) {
        overlay.style.removeProperty('--modal-viewport-height');
        overlay.style.removeProperty('--modal-viewport-top');
        return;
      }
      overlay.style.setProperty('--modal-viewport-height', `${viewport.height}px`);
      overlay.style.setProperty('--modal-viewport-top', `${viewport.offsetTop}px`);
    };
    update();
    viewport.addEventListener('resize', update);
    viewport.addEventListener('scroll', update);
    window.addEventListener('resize', update);
    return () => {
      viewport.removeEventListener('resize', update);
      viewport.removeEventListener('scroll', update);
      window.removeEventListener('resize', update);
      overlay.style.removeProperty('--modal-viewport-height');
      overlay.style.removeProperty('--modal-viewport-top');
    };
  }, [visible]);

  useEffect(() => {
    if (!visible || !onClose) return undefined;

    const handleKeyDown = event => {
      if (event.key === 'Escape') {
        onClose(event);
      }
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [visible, onClose]);

  if (!visible) return null;

  const handleOverlayClick = event => {
    if (typeof onClick === 'function') {
      onClick(event);
    }

    if (!event.defaultPrevented && event.target === event.currentTarget && onClose) {
      onClose(event);
    }
  };

  return (
    <div
      ref={overlayRef}
      className={`modal-overlay ${className}`}
      onClick={handleOverlayClick}
      {...props}
    >
      {children}
    </div>
  );
};

ModalOverlay.propTypes = {
  visible: PropTypes.bool,
  className: PropTypes.string,
  onClose: PropTypes.func,
  onClick: PropTypes.func,
  // Allow any other props like onClick
  children: PropTypes.node.isRequired,
};

export default ModalOverlay;
