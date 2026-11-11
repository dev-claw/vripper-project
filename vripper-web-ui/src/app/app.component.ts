import { CommonModule, NgIf } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  signal,
  WritableSignal,
} from '@angular/core';
import { MatBadgeModule } from '@angular/material/badge';
import {
  MatDialog,
  MatDialogModule,
  MatDialogRef,
} from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { RxStompState } from '@stomp/rx-stomp';
import { EMPTY, mergeMap, Subject } from 'rxjs';
import {
  ConfirmComponent,
  ConfirmDialogData,
} from './confirm/confirm.component';
import { Post } from './domain/post.model';
import { Settings } from './domain/settings.model';
import { DownloadTableComponent } from './download-table/download-table.component';
import { LogTableComponent } from './log-table/log-table.component';
import {
  RenameDialogComponent,
  RenameDialogData,
  RenameDialogResult,
} from './rename-dialog/rename-dialog.component';
import { ApplicationEndpointService } from './services/application-endpoint.service';
import { SettingsComponent } from './settings/settings.component';
import { StatusBarComponent } from './status-bar/status-bar.component';
import { SubscriptionTableComponent } from './subscription-table/subscription-table.component';
import { ThreadTableComponent } from './thread-table/thread-table.component';
import { ToolbarComponent } from './toolbar/toolbar.component';

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    MatTabsModule,
    MatIconModule,
    MatBadgeModule,
    DownloadTableComponent,
    ThreadTableComponent,
    SubscriptionTableComponent,
    LogTableComponent,
    ToolbarComponent,
    MatDialogModule,
    NgIf,
    StatusBarComponent,
  ],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
})
export class AppComponent {
  downloadCount = signal(0);
  threadCount = signal(0);
  subscriptionCount = signal(0);
  logCount = signal(0);
  tabIndex = signal(0);
  clearLogs = new Subject<void>();
  clearThreads = new Subject<void>();

  selectedPost: WritableSignal<Post | null> = signal(null);
  selectedPosts = signal([] as Post[]);
  noPostSelected = computed(() => this.selectedPosts().length === 0);
  loading = computed(
    () => this.applicationEndpoint.connectionState() != RxStompState.OPEN
  );

  constructor(
    private applicationEndpoint: ApplicationEndpointService,
    private dialog: MatDialog
  ) {}

  onTabChange = (index: number) => {
    this.tabIndex.set(index);
  };

  onPostsSelectionChange = (selectedPosts: Post[]) => {
    this.selectedPosts.set(selectedPosts);
  };

  onPostSelectionChange = (selectedPost: Post) => {
    this.selectedPost.set(selectedPost);
  };

  onStartSelected = () => {
    if (this.selectedPosts().length > 0) {
      this.applicationEndpoint.startPosts(this.selectedPosts()).subscribe();
    }
  };

  onStopSelected = () => {
    if (this.selectedPosts().length > 0) {
      this.applicationEndpoint.stopPosts(this.selectedPosts()).subscribe();
    }
  };

  onStartDownload = () => {
    this.applicationEndpoint.startDownload().subscribe();
  };

  onStopDownload = () => {
    this.applicationEndpoint.stopDownload().subscribe();
  };

  onLinks = (links: string) => {
    this.applicationEndpoint.links(links).subscribe();
  };

  onSettings = () => {
    this.applicationEndpoint.settings().subscribe((s) =>
      this.dialog.open<SettingsComponent, Settings>(SettingsComponent, {
        data: s,
        maxWidth: '100vw',
        maxHeight: '100vh',
        width: '80vw',
        height: '80vh',
      })
    );
  };

  onDeleteSelected = () => {
    const confirmDialog: MatDialogRef<ConfirmComponent, ConfirmDialogData> =
      this.dialog.open<ConfirmComponent, ConfirmDialogData>(ConfirmComponent, {
        data: {
          message: `Confirm removal of ${this.selectedPosts().length} post${
            this.selectedPosts().length > 1 ? 's' : ''
          }`,
          confirmCallback: () =>
            this.applicationEndpoint
              .deletePosts(this.selectedPosts())
              .subscribe(() => confirmDialog.close()),
        },
      });
  };

  onRenameSelected = () => {
    if (this.selectedPost() == null) {
      return;
    }
    const post = this.selectedPost();
    if (post?.postId == null || post?.folderName == null) {
      return;
    }
    const dialog: MatDialogRef<RenameDialogComponent, RenameDialogResult> =
      this.dialog.open<
        RenameDialogComponent,
        RenameDialogData,
        RenameDialogResult
      >(RenameDialogComponent, {
        data: {
          postId: post.postId,
          name: post.folderName,
        },
      });
    dialog
      .afterClosed()
      .pipe(
        mergeMap((result) => {
          if (result) {
            return this.applicationEndpoint.renamePost(result);
          } else {
            return EMPTY;
          }
        })
      )
      .subscribe();
  };

  onClearDownloads = () => {
    const confirmDialog: MatDialogRef<ConfirmComponent, ConfirmDialogData> =
      this.dialog.open<ConfirmComponent, ConfirmDialogData>(ConfirmComponent, {
        data: {
          message: `Confirm removal of finished posts${
            this.selectedPosts().length > 1 ? 's' : ''
          }`,
          confirmCallback: () =>
            this.applicationEndpoint
              .clearDownloads()
              .subscribe(() => confirmDialog.close()),
        },
      });
  };

  onClearLogs = () => {
    const confirmDialog: MatDialogRef<ConfirmComponent, ConfirmDialogData> =
      this.dialog.open<ConfirmComponent, ConfirmDialogData>(ConfirmComponent, {
        data: {
          message: `Confirm removal of logs${
            this.selectedPosts().length > 1 ? 's' : ''
          }`,
          confirmCallback: () => {
            this.clearLogs.next();
            confirmDialog.close();
          },
        },
      });
  };

  onRefreshSubscriptions = () => {
    this.applicationEndpoint.refreshSubscriptions().subscribe();
  };

  onClearSubscriptions = () => {
    const confirmDialog: MatDialogRef<ConfirmComponent, ConfirmDialogData> =
      this.dialog.open<ConfirmComponent, ConfirmDialogData>(ConfirmComponent, {
        data: {
          message: `Confirm clearing all subscriptions?`,
          confirmCallback: () => {
            this.applicationEndpoint.clearSubscriptions().subscribe(() => {
              confirmDialog.close();
            });
          },
        },
      });
  };

  onClearThreads = () => {
    const confirmDialog: MatDialogRef<ConfirmComponent, ConfirmDialogData> =
      this.dialog.open<ConfirmComponent, ConfirmDialogData>(ConfirmComponent, {
        data: {
          message: `Confirm clearing the Thread Library?`,
          confirmCallback: () =>
            this.applicationEndpoint.clearThreads().subscribe(() => {
              confirmDialog.close();
              this.clearThreads.next();
            }),
        },
      });
  };
}
