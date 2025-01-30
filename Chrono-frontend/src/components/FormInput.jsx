import 'react';

const FormInput = ({ label, type, name, value, onChange }) => {
    return (
        <div style={{ marginBottom: '1rem' }}>
            <label>
                {label}
                <input
                    style={{ marginLeft: '0.5rem' }}
                    type={type}
                    name={name}
                    value={value}
                    onChange={onChange}
                />
            </label>
        </div>
    );
};

export default FormInput;
