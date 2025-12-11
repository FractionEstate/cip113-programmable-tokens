/**
 * Application Providers Component
 *
 * Root provider wrapper that sets up all required context providers
 * for the CIP-113 application. This includes:
 * - MeshProvider for Cardano wallet connectivity
 * - Toast notifications (Toaster)
 * - Layout structure (Header, Footer)
 *
 * ## Provider Hierarchy
 * ```
 * MeshProvider
 *   └── Layout Container
 *         ├── Header
 *         ├── Main Content ({children})
 *         ├── Footer
 *         └── Toaster
 * ```
 *
 * @module components/providers/app-providers
 */

"use client";

import { ReactNode, useState, useEffect } from "react";
import { Header } from "@/components/layout/header";
import { Footer } from "@/components/layout/footer";
import { Toaster } from "@/components/ui/toast";
import { ProtocolVersionProvider } from "@/contexts/protocol-version-context";

/**
 * Props for AppProviders component.
 */
interface AppProvidersProps {
  /** Application content to wrap with providers */
  children: ReactNode;
}

/**
 * Root provider component with layout structure.
 *
 * Wraps the application with MeshProvider for wallet access
 * and provides the common layout elements. Uses lazy loading
 * for MeshProvider to handle WebAssembly initialization.
 *
 * @param props - Component props
 * @returns React component
 */
export function AppProviders({ children }: AppProvidersProps) {
  return (
    <MeshProvider>
      <ProtocolVersionProvider>
        <div className="flex flex-col min-h-screen bg-dark-900">
          <Header />
          <main className="flex-1">{children}</main>
          <Footer />
          <Toaster />
        </div>
      </ProtocolVersionProvider>
    </MeshProvider>
  );

  if (MeshProviderComponent) {
    return <MeshProviderComponent>{content}</MeshProviderComponent>;
  }

  return content;
}
