import { useContext } from "react";
import { AuthContext } from "../context/AuthContext";

const useAuth = () => {

    return useContext(AuthContext); // Kontext verwenden

};

export default useAuth;
