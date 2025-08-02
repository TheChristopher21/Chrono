import React from "react";

class ErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = { hasError: false };
    }

    static getDerivedStateFromError() {
        return { hasError: true };
    }

    componentDidCatch(error, info) {
        console.error("Error caught by ErrorBoundary:", error, info);
    }

    handleReload = () => {
        window.location.reload();
    };

    render() {
        if (this.state.hasError) {
            return (
                <div role="alert" style={{ padding: "2rem", textAlign: "center" }}>
                    <p>Es ist ein unerwarteter Fehler aufgetreten.</p>
                    <button type="button" onClick={this.handleReload}>Neu laden</button>
                </div>
            );
        }

        return this.props.children;
    }
}

export default ErrorBoundary;
