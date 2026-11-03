// 페이지 렌더 요청 큐. 엔진은 문서 하나를 한 번에 한 작업씩 처리하므로 요청을 하나씩 보내고,
// 화면에서 벗어난 페이지의 요청은 보내기 전에 버린다. 우선순위 숫자가 작을수록 먼저.

interface Job {
  priority: number;
  run: () => Promise<void>;
}

export class RenderQueue {
  private jobs = new Map<string, Job>();
  private running = false;

  /** 같은 key 의 대기 요청은 새 요청으로 바뀐다. */
  request(key: string, priority: number, run: () => Promise<void>) {
    this.jobs.set(key, { priority, run });
    void this.pump();
  }

  cancel(key: string) {
    this.jobs.delete(key);
  }

  clear() {
    this.jobs.clear();
  }

  private async pump() {
    if (this.running) return;
    this.running = true;
    try {
      while (this.jobs.size > 0) {
        let nextKey = "";
        let next: Job | undefined;
        for (const [key, job] of this.jobs) {
          if (!next || job.priority < next.priority) {
            next = job;
            nextKey = key;
          }
        }
        this.jobs.delete(nextKey);
        try {
          await next!.run();
        } catch {
          // 개별 페이지 실패는 그 페이지가 표시한다.
        }
      }
    } finally {
      this.running = false;
    }
  }
}
