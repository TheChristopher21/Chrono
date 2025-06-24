import React from 'react';
import PropTypes from 'prop-types';
import useModalOpen from '../utils/useModalOpen';

const ModalOverlay = ({ visible = true, children, className = '', ...props }) => {
  useModalOpen(visible);
  if (!visible) return null;

  return (
    <div className={`modal-overlay ${className}`} {...props}> 
      {children}
    </div>
  );
};

ModalOverlay.propTypes = {
  visible: PropTypes.bool,
  className: PropTypes.string,
  // Allow any other props like onClick
  children: PropTypes.node.isRequired,
};

export default ModalOverlay;
