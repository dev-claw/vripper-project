import { DataSource } from '@angular/cdk/collections';
import { CommonModule } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  OnDestroy,
  Output,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { BehaviorSubject, Observable, Subscription } from 'rxjs';
import { ThreadRow } from '../domain/thread-row.model';
import { Thread } from '../domain/thread.model';
import { ApplicationEndpointService } from '../services/application-endpoint.service';
import {
  ThreadDialogData,
  ThreadSelectionComponent,
} from '../thread-selection/thread-selection.component';

@Component({
  selector: 'app-subscription-table',
  imports: [
    CommonModule,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatTableModule,
  ],
  templateUrl: './subscription-table.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: true,
})
export class SubscriptionTableComponent implements OnDestroy {
  dataSource = new SubscriptionDataSource(this.applicationEndpoint);
  displayedColumns: string[] = [
    'title',
    'link',
    'total',
    'forum',
    'starter',
    'lastPost',
    'lastPoster',
    'replyCount',
    'views',
  ];
  @Output()
  rowCountChange = new EventEmitter<number>();
  private readonly rowCountSubscription: Subscription;

  constructor(
    private applicationEndpoint: ApplicationEndpointService,
    private dialog: MatDialog
  ) {
    this.rowCountSubscription = this.dataSource._dataStream.subscribe((v) =>
      this.rowCountChange.emit(v.length)
    );
  }

  ngOnDestroy(): void {
    this.rowCountSubscription.unsubscribe();
    this.dataSource.disconnect();
  }

  onRowDoubleClicked(row: ThreadRow) {
    const data: ThreadDialogData = { threadId: row.threadId };
    this.dialog.open(ThreadSelectionComponent, {
      data,
      maxWidth: '100vw',
      maxHeight: '100vh',
      width: '80vw',
      height: '80vh',
    });
  }
}

class SubscriptionDataSource extends DataSource<Thread> {
  subscriptions: Subscription[] = [];
  _dataStream = new BehaviorSubject<Thread[]>([]);

  constructor(private applicationEndpoint: ApplicationEndpointService) {
    super();
  }

  connect(): Observable<Thread[]> {
    this.disconnect();
    this.subscriptions.push(
      this.applicationEndpoint.subscriptions$.subscribe(
        (newSubscriptions: Thread[]) => {
          const rows = new Map(
            this._dataStream.value.map((row) => [row.link, row])
          );
          newSubscriptions.forEach((subscription) =>
            rows.set(subscription.link, this.toRow(subscription))
          );
          this._dataStream.next([...rows.values()]);
        }
      )
    );
    this.subscriptions.push(
      this.applicationEndpoint.subscriptionRemove$.subscribe((e: number) => {
        this._dataStream.next([
          ...this._dataStream.value.filter((v) => v.threadId !== e),
        ]);
      })
    );
    this.subscriptions.push(
      this.applicationEndpoint.subscriptionRemoveAll$.subscribe(() => {
        this._dataStream.next([]);
      })
    );
    return this._dataStream.asObservable();
  }

  disconnect(): void {
    this.subscriptions.forEach((e) => e.unsubscribe());
    this.subscriptions = [];
  }

  private toRow(subscription: Thread): ThreadRow {
    return new ThreadRow(
      subscription.link,
      subscription.title,
      subscription.threadId,
      subscription.total,
      subscription.forum,
      subscription.starter,
      subscription.lastPost,
      subscription.lastPoster,
      subscription.replyCount,
      subscription.views,
      subscription.newestPostId,
      subscription.sticky,
      subscription.open
    );
  }
}
