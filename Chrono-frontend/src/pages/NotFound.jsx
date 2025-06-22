import 'react';
import { useTranslation } from '../context/LanguageContext';

const NotFound = () => {
    const { t } = useTranslation();
    return (
        <div>
            <h2>{t('notFound.pageNotFound')}</h2>
        </div>
    );
};

export default NotFound;
