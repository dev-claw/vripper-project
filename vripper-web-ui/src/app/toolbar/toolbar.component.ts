import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { CommonModule, NgIf } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ScanComponent } from '../scan/scan.component';

@Component({
  selector: 'app-toolbar',
  imports: [
    CommonModule,
    MatButtonModule,
    MatIconModule,
    MatToolbarModule,
    MatTooltipModule,
    MatDialogModule,
    NgIf,
    MatDividerModule,
  ],
  templateUrl: './toolbar.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
})
export class ToolbarComponent {
  selectedTab = input.required();
  links = output<string>();
  startSelected = output<void>();
  stopSelected = output<void>();
  deleteSelected = output<void>();
  renameSelected = output<void>();
  clearDownload = output<void>();
  clearLogs = output<void>();
  clearThreads = output<void>();
  clearSubscriptions = output<void>();
  refreshSubscriptions = output<void>();
  startDownload = output<void>();
  stopDownload = output<void>();
  settings = output<void>();
  disableSelected = input.required();

  handsetPortrait$ = this.breakpointObserver.observe(
    Breakpoints.HandsetPortrait
  );

  constructor(
    public dialog: MatDialog,
    private breakpointObserver: BreakpointObserver
  ) {}

  openScanDialog() {
    this.dialog
      .open<ScanComponent, never, string>(ScanComponent, {
        maxWidth: '100vw',
        maxHeight: '100vh',
      })
      .afterClosed()
      .subscribe((v) => {
        if (v) {
          this.links.emit(v);
        }
      });
  }

  startSelectedClick() {
    this.startSelected.emit();
  }

  stopSelectedClick() {
    this.stopSelected.emit();
  }

  startClick() {
    this.startDownload.emit();
  }

  stopClick() {
    this.stopDownload.emit();
  }

  settingsClick() {
    this.settings.emit();
  }

  removeSelectedClick() {
    this.deleteSelected.emit();
  }

  renameSelectedClick() {
    this.renameSelected.emit();
  }

  clearDownloadsClick() {
    this.clearDownload.emit();
  }

  clearLogsClick() {
    this.clearLogs.emit();
  }

  clearThreadsClick() {
    this.clearThreads.emit();
  }

  clearSubscriptionsClick() {
    this.clearSubscriptions.emit();
  }

  refreshSubscriptionsClick() {
    this.refreshSubscriptions.emit();
  }
}
