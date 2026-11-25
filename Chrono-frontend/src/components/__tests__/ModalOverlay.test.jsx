import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ModalOverlay from '../ModalOverlay.jsx';

afterEach(() => vi.unstubAllGlobals());

describe('ModalOverlay on mobile Safari', () => {
  it('follows keyboard resizing and panning, preserves pinch zoom and removes listeners on close', () => {
    const viewport = Object.assign(new EventTarget(), { height: 667, offsetTop: 0, scale: 1 });
    vi.stubGlobal('visualViewport', viewport);
    const { rerender, unmount } = render(<ModalOverlay><input aria-label="Grund" /></ModalOverlay>);
    const overlay = screen.getByLabelText('Grund').parentElement;
    expect(overlay.style.getPropertyValue('--modal-viewport-height')).toBe('667px');

    act(() => {
      viewport.height = 310;
      viewport.offsetTop = 80;
      viewport.dispatchEvent(new Event('resize'));
    });
    expect(overlay.style.getPropertyValue('--modal-viewport-height')).toBe('310px');
    expect(overlay.style.getPropertyValue('--modal-viewport-top')).toBe('80px');

    act(() => {
      viewport.offsetTop = 110;
      viewport.dispatchEvent(new Event('scroll'));
    });
    expect(overlay.style.getPropertyValue('--modal-viewport-top')).toBe('110px');

    act(() => {
      viewport.scale = 2;
      viewport.dispatchEvent(new Event('resize'));
    });
    expect(overlay.style.getPropertyValue('--modal-viewport-height')).toBe('');
    expect(overlay.style.getPropertyValue('--modal-viewport-top')).toBe('');

    act(() => {
      Object.assign(viewport, { scale: 1, height: 667, offsetTop: 0 });
      viewport.dispatchEvent(new Event('resize'));
    });
    expect(overlay.style.getPropertyValue('--modal-viewport-height')).toBe('667px');

    rerender(<ModalOverlay visible={false}><input aria-label="Grund" /></ModalOverlay>);
    act(() => viewport.dispatchEvent(new Event('resize')));
    expect(overlay.style.getPropertyValue('--modal-viewport-height')).toBe('');
    unmount();
  });

  it('still renders without the Visual Viewport API', () => {
    vi.stubGlobal('visualViewport', undefined);
    render(<ModalOverlay><input aria-label="Grund" /></ModalOverlay>);
    expect(screen.getByLabelText('Grund')).toBeVisible();
  });
});
