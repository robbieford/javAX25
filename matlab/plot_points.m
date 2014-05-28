function [locs] = plot_points( file_name, plot_num )

    %close all;
    figure(plot_num)
    grid on
    
    [y,FS,NBITS]=wavread(file_name);
    
    y = abs(y);
    
    v = zeros(1, length(y));
    
    for i=1 : length(y)-1
        if(i>2)
            v(i) = (v(i-2)+v(i-1)+y(i))/3;
        end
    end
    
    x2 = [1:1:length(y)];
    x3 = [1:1:length(v)];

    locs = detectPeaks(v);
    
    hold on;
    plot(x2, y, 'r');%y(:,1));
    plot(x2, zeros(1,length(x2)));
    plot(x3, v, 'c');
    for i=1 : length(locs)
        line([locs(i) locs(i)],[0 v(locs(i))]);
    end
end