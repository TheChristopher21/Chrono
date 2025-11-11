import React, { useEffect } from 'react';
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
  useModalOpen(visible);

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
