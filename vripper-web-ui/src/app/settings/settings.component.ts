import { DialogRef } from '@angular/cdk/dialog';
import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { CommonModule, NgForOf, NgIf } from '@angular/common';
import { Component, Inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatOptionModule } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import {
  ConnectionSettings,
  DownloadSettings,
  Settings,
  SystemSettings,
  ViperSettings,
} from '../domain/settings.model';
import { ApplicationEndpointService } from '../services/application-endpoint.service';

@Component({
  selector: 'app-settings',
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatOptionModule,
    MatSelectModule,
    MatTabsModule,
    MatTooltipModule,
    NgForOf,
    NgIf,
    ReactiveFormsModule,
    MatSlideToggleModule,
  ],
  templateUrl: './settings.component.html',
  standalone: true,
})
export class SettingsComponent {
  mirrors = signal([]);

  viperGirlsSettingsForm = new FormGroup({
    login: new FormControl(false),
    username: new FormControl(''),
    password: new FormControl(''),
    thanks: new FormControl(false),
    host: new FormControl(''),
    requestLimit: new FormControl(2),
    fetchMetadata: new FormControl(false),
    watchSubscriptions: new FormControl(false),
    watchIntervalMinutes: new FormControl(15),
  });

  downloadSettingsForm = new FormGroup({
    downloadPath: new FormControl(''),
    autoStart: new FormControl(false),
    autoQueueThreshold: new FormControl(0),
    forceOrder: new FormControl(false),
    forumSubDirectory: new FormControl(false),
    threadSubLocation: new FormControl(false),
    clearCompleted: new FormControl(false),
    appendPostId: new FormControl(false),
  });

  connectionSettingsForm = new FormGroup({
    maxConcurrentPerHost: new FormControl(0),
    maxGlobalConcurrent: new FormControl(0),
    timeout: new FormControl(0),
    maxAttempts: new FormControl(0),
  });

  systemSettingsForm = new FormGroup({
    tempPath: new FormControl(''),
    maxEventLog: new FormControl(0),
    enableClipboardMonitoring: new FormControl(false),
    clipboardPollingRate: new FormControl(500),
  });

  constructor(
    @Inject(MAT_DIALOG_DATA) public data: Settings,
    public dialogRef: DialogRef<Settings>,
    private applicationEndpoint: ApplicationEndpointService,
    breakpointObserver: BreakpointObserver
  ) {
    this.viperGirlsSettingsForm.reset(data.viperSettings);
    this.downloadSettingsForm.reset(data.downloadSettings);
    this.connectionSettingsForm.reset(data.connectionSettings);
    this.systemSettingsForm.reset(data.systemSettings);
    breakpointObserver
      .observe(Breakpoints.HandsetPortrait)
      .subscribe((result) => {
        if (result.matches) {
          this.dialogRef.updateSize('100vw', '80vh');
        } else {
          this.dialogRef.updateSize('80vw', '80vh');
        }
      });
  }

  save = () => {
    this.applicationEndpoint
      .newSettings({
        viperSettings: {
          ...(this.viperGirlsSettingsForm.value as ViperSettings),
        },
        connectionSettings: {
          ...(this.connectionSettingsForm.value as ConnectionSettings),
        },
        downloadSettings: {
          ...(this.downloadSettingsForm.value as DownloadSettings),
        },
        systemSettings: {
          ...(this.systemSettingsForm.value as SystemSettings),
        },
      } as Settings)
      .subscribe(() => this.dialogRef.close());
  };
}
