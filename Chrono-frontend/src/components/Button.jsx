import React from 'react';

const Button = ({ text, onClick, type = 'button', className = '' }) => {
    return (
        <button
            type={type}
            className={`px-4 py-2 bg-blue-500 text-white rounded ${className}`}
            onClick={onClick}
        >
            {text}
        </button>
    );
};

export default Button;
