(() => {
  try {
    const storedTheme = localStorage.getItem('theme');
    const theme = storedTheme === 'dark' ? 'dark' : 'light';
    document.documentElement.setAttribute('data-theme', theme);
    document.documentElement.style.colorScheme = theme;
  } catch {
    document.documentElement.setAttribute('data-theme', 'light');
  }
})();
