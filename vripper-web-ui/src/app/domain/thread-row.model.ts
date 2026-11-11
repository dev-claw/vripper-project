import { Thread } from './thread.model';

export class ThreadRow extends Thread {
  constructor(
    link: string,
    title: string,
    threadId: number,
    total: number,
    forum: string = '',
    starter: string = '',
    lastPost: string = '',
    lastPoster: string = '',
    replyCount: number = 0,
    views: number = 0,
    newestPostId: number = 0,
    sticky: boolean = false,
    open: boolean = true
  ) {
    super(
      link,
      title,
      threadId,
      total,
      forum,
      starter,
      lastPost,
      lastPoster,
      replyCount,
      views,
      newestPostId,
      sticky,
      open
    );
  }
}
